package me.dfrsn.cqrsnav

import com.intellij.psi.PsiElement
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiModificationTracker
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.php.PhpIndex
import com.jetbrains.php.lang.psi.elements.ArrayHashElement
import com.jetbrains.php.lang.psi.elements.ClassConstantReference
import com.jetbrains.php.lang.psi.elements.ClassReference
import com.jetbrains.php.lang.psi.elements.Method
import com.jetbrains.php.lang.psi.elements.Parameter
import com.jetbrains.php.lang.psi.elements.PhpAttribute
import com.jetbrains.php.lang.psi.elements.PhpClass
import com.jetbrains.php.lang.psi.elements.StringLiteralExpression
import com.jetbrains.php.lang.psi.resolve.types.PhpType

/**
 * Links messages (commands, queries, events) to the methods that handle them, in both directions.
 *
 * A method handles a message when:
 * - it, or its class, has `#[AsMessageHandler]` (Messenger) or `#[AsEventListener]` (EventDispatcher). The message is
 *   the `handles` / `event` argument if given, otherwise the type of the method's first parameter. A class-level
 *   attribute targets its `method` argument, or `__invoke` by default.
 * - it is the `__invoke` of a class implementing Messenger's `MessageHandlerInterface`.
 * - its class implements `EventSubscriberInterface` and `getSubscribedEvents()` maps `Message::class` to it.
 * - as a fallback for handlers wired in services.yaml (`_instanceof`, custom interfaces), it is the `__invoke` of a
 *   class whose name ends with Handler, Listener or Subscriber.
 */
object MessageHandlers {

    private const val AS_MESSAGE_HANDLER = "\\Symfony\\Component\\Messenger\\Attribute\\AsMessageHandler"
    private const val AS_EVENT_LISTENER = "\\Symfony\\Component\\EventDispatcher\\Attribute\\AsEventListener"
    private const val MESSAGE_HANDLER_INTERFACE = "\\Symfony\\Component\\Messenger\\Handler\\MessageHandlerInterface"
    private const val EVENT_SUBSCRIBER_INTERFACE = "\\Symfony\\Component\\EventDispatcher\\EventSubscriberInterface"
    private const val INVOKE = "__invoke"
    private val CONVENTIONAL_HANDLER_NAME = Regex("(Handler|Listener|Subscriber)$")

    /** Methods handling [message], sorted by class and method name. */
    fun findHandlers(message: PhpClass): List<Method> =
        CachedValuesManager.getCachedValue(message) {
            CachedValueProvider.Result.create(
                computeHandlers(message),
                PsiModificationTracker.getInstance(message.project),
            )
        }

    /** Fully qualified names (with a leading backslash) of the messages handled by [method]. */
    fun findMessageFqns(method: Method): Set<String> {
        val phpClass = method.containingClass ?: return emptySet()
        val messages = linkedSetOf<String>()

        method.getAttributes(AS_MESSAGE_HANDLER).forEach { messages += attributeMessages(it, "handles", 2, method) }
        method.getAttributes(AS_EVENT_LISTENER).forEach { messages += attributeMessages(it, "event", 0, method) }

        phpClass.getAttributes(AS_MESSAGE_HANDLER)
            .filter { (stringArgument(it, "method", 3) ?: INVOKE) == method.name }
            .forEach { messages += attributeMessages(it, "handles", 2, method) }
        phpClass.getAttributes(AS_EVENT_LISTENER)
            .filter { (stringArgument(it, "method", 1) ?: INVOKE) == method.name }
            .forEach { messages += attributeMessages(it, "event", 0, method) }

        if (method.name == INVOKE && implements(phpClass, MESSAGE_HANDLER_INTERFACE)) {
            messages += firstParameterTypes(method)
        }
        if (implements(phpClass, EVENT_SUBSCRIBER_INTERFACE)) {
            messages += subscribedEvents(phpClass)[method.name].orEmpty()
        }
        if (messages.isEmpty() && method.name == INVOKE && CONVENTIONAL_HANDLER_NAME.containsMatchIn(phpClass.name)) {
            messages += firstParameterTypes(method)
        }

        return messages
    }

    /** Classes and interfaces of the messages handled by [method]. */
    fun findMessages(method: Method): List<PhpClass> {
        val phpIndex = PhpIndex.getInstance(method.project)
        return findMessageFqns(method).flatMap { phpIndex.getAnyByFQN(it) }.distinct()
    }

    private fun computeHandlers(message: PhpClass): List<Method> {
        val candidates = linkedSetOf<Method>()

        // Every way of declaring a handler mentions the message class: as a parameter type, in an attribute argument
        // or in getSubscribedEvents(). Searching its references keeps the lookup cheap.
        ReferencesSearch.search(message, GlobalSearchScope.projectScope(message.project)).forEach { reference ->
            val element = reference.element
            val parameter = PsiTreeUtil.getParentOfType(element, Parameter::class.java)
            if (parameter != null) {
                (parameter.parent?.parent as? Method)?.let { candidates += it }
            } else if (isInAttribute(element) || PsiTreeUtil.getParentOfType(element, Method::class.java)?.name == "getSubscribedEvents") {
                PsiTreeUtil.getParentOfType(element, PhpClass::class.java)?.ownMethods?.let { candidates += it }
            }
        }

        return candidates
            .filter { message.fqn in findMessageFqns(it) }
            .sortedWith(compareBy({ it.containingClass?.fqn }, { it.name }))
    }

    private fun isInAttribute(element: PsiElement) = PsiTreeUtil.getParentOfType(element, PhpAttribute::class.java) != null

    /** The message named by an attribute argument such as `handles: Foo::class`, or the method's first parameter type. */
    private fun attributeMessages(attribute: PhpAttribute, name: String, index: Int, method: Method): Collection<String> {
        val explicit = classFqn(attribute.parameterList?.getParameter(name, index))
        return if (explicit != null) listOf(explicit) else firstParameterTypes(method)
    }

    private fun stringArgument(attribute: PhpAttribute, name: String, index: Int): String? =
        (attribute.parameterList?.getParameter(name, index) as? StringLiteralExpression)?.contents

    /** The class named by `Foo::class` or `'App\Foo'`, as a fully qualified name. */
    private fun classFqn(element: PsiElement?): String? = when {
        element is ClassConstantReference && element.name == "class" -> (element.classReference as? ClassReference)?.fqn
        element is StringLiteralExpression && element.contents.isNotBlank() -> "\\" + element.contents.trimStart('\\')
        else -> null
    }

    private fun firstParameterTypes(method: Method): List<String> =
        method.parameters.firstOrNull()?.declaredType?.types.orEmpty()
            .filter { it.startsWith("\\") && !PhpType.isPrimitiveType(it) }

    private fun implements(phpClass: PhpClass, interfaceFqn: String): Boolean =
        generateSequence(phpClass) { it.superClass }.take(20).any { interfaceFqn in it.interfaceNames }

    /** Maps each method of an event subscriber to the event classes it subscribes to in getSubscribedEvents(). */
    private fun subscribedEvents(subscriber: PhpClass): Map<String, Set<String>> =
        CachedValuesManager.getCachedValue(subscriber) {
            val events = mutableMapOf<String, MutableSet<String>>()
            val method = subscriber.findOwnMethodByName("getSubscribedEvents")
            PsiTreeUtil.findChildrenOfType(method, ArrayHashElement::class.java).forEach { entry ->
                val event = classFqn(entry.key) ?: return@forEach
                // The value is 'method', ['method', priority] or [['method1', priority], ['method2']].
                val value = entry.value ?: return@forEach
                val methodNames = (listOf(value) + PsiTreeUtil.findChildrenOfType(value, StringLiteralExpression::class.java))
                    .filterIsInstance<StringLiteralExpression>()
                    .map { it.contents }
                methodNames.forEach { events.getOrPut(it) { linkedSetOf() } += event }
            }
            CachedValueProvider.Result.create(events, PsiModificationTracker.getInstance(subscriber.project))
        }
}
