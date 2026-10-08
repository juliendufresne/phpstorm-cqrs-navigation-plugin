package me.dfrsn.cqrsnav

import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.codeInsight.daemon.RelatedItemLineMarkerInfo
import com.intellij.navigation.GotoRelatedProvider
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.psi.PsiElement
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.jetbrains.php.PhpIndex
import com.jetbrains.php.lang.psi.elements.Method
import com.jetbrains.php.lang.psi.elements.PhpClass
import com.jetbrains.php.lang.psi.elements.PhpNamedElement

class MessageHandlersTest : BasePlatformTestCase() {

    override fun getTestDataPath() = "src/test/testData"

    override fun setUp() {
        super.setUp()
        myFixture.copyDirectoryToProject("project", "")
    }

    fun testClassLevelMessageHandler() = assertHandlers("App\\Command\\CreateUser", "CreateUserHandler::__invoke")

    fun testHandlesAndMethodArguments() = assertHandlers("App\\Command\\DeleteUser", "UserCommands::handle")

    fun testMethodLevelMessageHandlers() =
        assertHandlers("App\\Command\\RenameUser", "RenameUserHandlers::onRename", "RenameUserHandlers::onRenameAgain")

    fun testMessageHandlerInterface() = assertHandlers("App\\Command\\LegacyCommand", "LegacyHandler::__invoke")

    fun testNamingConventionFallback() = assertHandlers("App\\Command\\ArchiveUser", "ArchiveUserHandler::__invoke")

    fun testMessageWithoutHandler() = assertHandlers("App\\Command\\Orphan")

    fun testEventListenersAndSubscribers() = assertHandlers(
        "App\\Event\\UserCreated",
        "Audit::log",
        "SendWelcomeEmail::__invoke",
        "UserSubscriber::onCreated",
    )

    fun testSubscriberWithPriorities() = assertHandlers("App\\Event\\UserDeleted", "UserSubscriber::first", "UserSubscriber::second")

    fun testHandlerToMessage() {
        assertMessages("App\\Command\\CreateUserHandler", "__invoke", "CreateUser")
        assertMessages("App\\Command\\UserCommands", "handle", "DeleteUser")
        assertMessages("App\\Event\\Audit", "log", "UserCreated")
        assertMessages("App\\Event\\UserSubscriber", "first", "UserDeleted")
        assertMessages("App\\Command\\UserCommands", "notAHandler")
        assertMessages("App\\Command\\UserService", "__invoke")
        assertMessages("App\\Event\\UserSubscriber", "getSubscribedEvents")
    }

    fun testGutterOnMessageClass() {
        myFixture.configureFromTempProjectFile("src/Event/Events.php")
        moveCaretTo("UserCreated {}")
        assertEquals(
            listOf("Audit::log", "SendWelcomeEmail::__invoke", "UserSubscriber::onCreated"),
            gutterTargetsAtCaret(),
        )
    }

    fun testGutterOnNewExpression() {
        myFixture.configureFromTempProjectFile("src/Command/Handlers.php")
        moveCaretTo("CreateUser();")
        assertEquals(listOf("CreateUserHandler::__invoke"), gutterTargetsAtCaret())

        moveCaretTo("Orphan();")
        assertEmpty(myFixture.findGuttersAtCaret())
    }

    fun testGutterOnHandlerMethod() {
        myFixture.configureFromTempProjectFile("src/Command/Handlers.php")
        moveCaretTo("__invoke(CreateUser")
        assertEquals(listOf("CreateUser"), gutterTargetsAtCaret())
    }

    fun testGotoRelatedSymbol() {
        myFixture.configureFromTempProjectFile("src/Command/Messages.php")
        moveCaretTo("CreateUser {}")
        val element = myFixture.file.findElementAt(myFixture.caretOffset)!!
        val related = ExtensionPointName<GotoRelatedProvider>("com.intellij.gotoRelatedProvider").extensionList.flatMap { it.getItems(element) }.map { describe(it.element!!) }
        assertEquals(listOf("CreateUserHandler::__invoke"), related)
    }

    private fun assertHandlers(messageFqn: String, vararg expected: String) {
        val handlers = MessageHandlers.findHandlers(phpClass(messageFqn)).map(::describe)
        assertEquals(expected.toList(), handlers)
    }

    private fun assertMessages(classFqn: String, methodName: String, vararg expected: String) {
        val method = phpClass(classFqn).findOwnMethodByName(methodName)!!
        assertEquals("$classFqn::$methodName", expected.toList(), MessageHandlers.findMessages(method).map(::describe))
    }

    private fun phpClass(fqn: String): PhpClass = PhpIndex.getInstance(project).getClassesByFQN(fqn).single()

    private fun moveCaretTo(text: String) {
        val offset = myFixture.editor.document.text.indexOf(text)
        assertTrue("'$text' not found", offset >= 0)
        myFixture.editor.caretModel.moveToOffset(offset)
    }

    private fun gutterTargetsAtCaret(): List<String> {
        val gutter = myFixture.findGuttersAtCaret().single() as LineMarkerInfo.LineMarkerGutterIconRenderer<*>
        val info = gutter.lineMarkerInfo
        assertNotNull(info.navigationHandler)
        return (info as RelatedItemLineMarkerInfo<*>).createGotoRelatedItems()
            .map { describe(it.element!!) }
    }

    private fun describe(element: PsiElement): String = when (element) {
        is Method -> "${element.containingClass?.name}::${element.name}"
        is PhpNamedElement -> element.name
        else -> element.text
    }
}
