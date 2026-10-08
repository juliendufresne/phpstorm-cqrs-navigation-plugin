package me.dfrsn.cqrsnav

import com.intellij.codeInsight.daemon.RelatedItemLineMarkerInfo
import com.intellij.codeInsight.daemon.RelatedItemLineMarkerProvider
import com.intellij.codeInsight.navigation.NavigationGutterIconBuilder
import com.intellij.icons.AllIcons
import com.intellij.psi.PsiElement
import com.jetbrains.php.lang.lexer.PhpTokenTypes
import com.jetbrains.php.lang.psi.elements.ClassReference
import com.jetbrains.php.lang.psi.elements.Method
import com.jetbrains.php.lang.psi.elements.NewExpression
import com.jetbrains.php.lang.psi.elements.PhpClass
import com.jetbrains.php.lang.psi.elements.PhpNamedElement
import javax.swing.Icon

/**
 * Gutter icons linking messages and their handlers:
 * - on a message class declaration and on `new Message(...)`: go to its handlers or listeners
 * - on a handler method: go back to the message it handles
 *
 * Being a [RelatedItemLineMarkerProvider], the same targets are offered by Navigate > Related Symbol.
 */
class MessageHandlerLineMarkerProvider : RelatedItemLineMarkerProvider() {

    override fun getName() = "Message handlers"

    override fun getIcon(): Icon = HANDLERS_ICON

    override fun collectNavigationMarkers(element: PsiElement, result: MutableCollection<in RelatedItemLineMarkerInfo<*>>) {
        // Line markers must be registered on leaf elements, so react to identifiers only.
        if (element.node?.elementType != PhpTokenTypes.IDENTIFIER) return

        when (val parent = element.parent) {
            is PhpClass -> if (parent.nameIdentifier == element) {
                handlersMarker(element, MessageHandlers.findHandlers(parent))?.let(result::add)
            }

            is Method -> if (parent.nameIdentifier == element) {
                messagesMarker(element, MessageHandlers.findMessages(parent))?.let(result::add)
            }

            is ClassReference -> if (parent.parent is NewExpression) {
                val message = parent.resolve() as? PhpClass ?: return
                handlersMarker(element, MessageHandlers.findHandlers(message))?.let(result::add)
            }
        }
    }

    private fun handlersMarker(anchor: PsiElement, handlers: List<Method>) =
        marker(anchor, handlers, HANDLERS_ICON, "Navigate to handler", "Choose Handler")

    private fun messagesMarker(anchor: PsiElement, messages: List<PhpClass>) =
        marker(anchor, messages, MESSAGES_ICON, "Navigate to handled message", "Choose Message")

    private fun marker(anchor: PsiElement, targets: List<PhpNamedElement>, icon: Icon, tooltip: String, popupTitle: String) =
        if (targets.isEmpty()) {
            null
        } else {
            NavigationGutterIconBuilder.create(icon)
                .setTargets(targets)
                .setTooltipText(if (targets.size == 1) "$tooltip ${describe(targets.single())}" else "${tooltip}s")
                .setPopupTitle(popupTitle)
                .createLineMarkerInfo(anchor)
        }

    private fun describe(target: PhpNamedElement): String = when (target) {
        is Method -> "${target.containingClass?.name}::${target.name}()"
        else -> target.name
    }

    private companion object {
        val HANDLERS_ICON: Icon = AllIcons.Actions.Forward
        val MESSAGES_ICON: Icon = AllIcons.Actions.Back
    }
}
