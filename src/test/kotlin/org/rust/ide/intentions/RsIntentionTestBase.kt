/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.intentions

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInsight.intention.IntentionActionDelegate
import com.intellij.codeInsight.intention.IntentionManager
import com.intellij.codeInsight.intention.impl.preview.IntentionPreviewPopupUpdateProcessor
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo
import com.intellij.codeInsight.template.impl.TemplateManagerImpl
import com.intellij.openapi.util.TextRange
import com.intellij.util.PathUtil
import com.intellij.util.ui.UIUtil
import org.intellij.lang.annotations.Language
import org.rust.fileTreeFromText
import kotlin.reflect.KClass
import kotlin.reflect.full.isSubclassOf

abstract class RsIntentionTestBase(private val intentionClass: KClass<out IntentionAction>) : RsIntentionTestPlatformBase() {

    protected val intention: IntentionAction
        get() = findIntention() ?: error("Failed to find `${intentionClass.simpleName}` intention")

    protected open val previewExpected: Boolean get() = intention.startInWriteAction()

    fun `test intention has documentation`() {
        if (!intentionClass.isSubclassOf(RsElementBaseIntentionAction::class)) return

        val directory = "intentionDescriptions/${intentionClass.simpleName}"
        val description = checkFileExists("$directory/description.html")
        checkHtmlStyle(description)

        checkFileExists("$directory/before.rs.template")
        checkFileExists("$directory/after.rs.template")
    }

    private fun checkFileExists(path: String): String = getResourceAsString(path)
        ?: error("No ${PathUtil.getFileName(path)} found for $intentionClass ($path)")

    protected fun doAvailableTest(
        @Language("Rust") before: String,
        @Language("Rust") after: String,
        @Language("Rust") preview: String? = null,
        fileName: String = "main.rs"
    ) {
        InlineFile(before.trimIndent(), fileName).withCaret()
        launchAction(preview?.trimIndent())
        myFixture.checkResult(replaceCaretMarker(after.trimIndent()))
    }

    protected fun doAvailableSymmetricTest(
        @Language("Rust") before: String,
        @Language("Rust") after: String,
    ) {
        doAvailableTest(before, after.replace("/*caret*/", ""))
        doAvailableTest(after, before.replace("/*caret*/", ""))
    }

    protected fun doAvailableTestWithLiveTemplate(
        @Language("Rust") before: String,
        toType: String,
        @Language("Rust") after: String,
        fileName: String = "main.rs"
    ) {
        TemplateManagerImpl.setTemplateTesting(testRootDisposable)
        InlineFile(before.trimIndent(), fileName).withCaret()
        launchAction()
        assertNotNull(TemplateManagerImpl.getTemplateState(myFixture.editor))
        myFixture.type(toType)
        assertNull(TemplateManagerImpl.getTemplateState(myFixture.editor))
        myFixture.checkResult(replaceCaretMarker(after.trimIndent()))
    }

    @Suppress("unused")
    protected fun doAvailableTestWithFileTree(
        @Language("Rust") fileStructureBefore: String,
        @Language("Rust") openedFileAfter: String
    ) {
        fileTreeFromText(fileStructureBefore).createAndOpenFileWithCaretMarker()
        launchAction()
        myFixture.checkResult(replaceCaretMarker(openedFileAfter.trimIndent()))
    }

    protected fun doAvailableTestWithFileTreeComplete(
        @Language("Rust") fileStructureBefore: String,
        @Language("Rust") fileStructureAfter: String
    ) {
        fileTreeFromText(fileStructureBefore).createAndOpenFileWithCaretMarker()
        launchAction()
        fileTreeFromText(replaceCaretMarker(fileStructureAfter)).check(myFixture)
    }

    protected fun launchAction(@Language("Rust") preview: String? = null) {
        UIUtil.dispatchAllInvocationEvents()
        // Check preview only for intentions from Rust plugin
        if (intentionClass.isSubclassOf(RsElementBaseIntentionAction::class)) {
            if (previewExpected) {
                checkPreviewAndLaunchAction(intention, preview)
            } else {
                val intention = intention
                val previewInfo = IntentionPreviewPopupUpdateProcessor.getPreviewInfo(project, intention, myFixture.file, myFixture.editor)
                assertEquals(IntentionPreviewInfo.EMPTY, previewInfo)
                myFixture.launchAction(intention)
            }
        } else {
            myFixture.launchAction(intention)
        }
    }

    protected fun doUnavailableTest(@Language("Rust") before: String, fileName: String = "main.rs") {
        InlineFile(before, fileName).withCaret()
        val intention = findIntention()
        check(intention == null) {
            "\"${intentionClass.simpleName}\" should not be available"
        }
    }

    private fun findIntention(): IntentionAction? {
        return IntentionManager.getInstance().intentionActions.firstOrNull {
            val originalIntention = IntentionActionDelegate.unwrap(it)
            intentionClass == originalIntention::class
        }?.takeIf { it.isAvailable(project, myFixture.editor, myFixture.file) }
    }

    protected fun checkAvailableInSelectionOnly(@Language("Rust") code: String, fileName: String = "main.rs") {
        InlineFile(code.replace("<selection>", "<selection><caret>"), fileName)
        val selections = myFixture.editor.selectionModel.let { model ->
            model.blockSelectionStarts.zip(model.blockSelectionEnds)
                .map { TextRange(it.first, it.second + 1) }
        }
        val intention = IntentionManager.getInstance().intentionActions.find {
            IntentionActionDelegate.unwrap(it).javaClass == intentionClass.java
        } ?: error("Intention action with class $intentionClass is not registered")
        for (pos in myFixture.file.text.indices) {
            myFixture.editor.caretModel.moveToOffset(pos)
            val expectAvailable = selections.any { it.contains(pos) }
            val isAvailable = intention.isAvailable(project, myFixture.editor, myFixture.file)
            check(isAvailable == expectAvailable) {
                "Expect ${if (expectAvailable) "available" else "unavailable"}, " +
                    "actually ${if (isAvailable) "available" else "unavailable"} " +
                    "at `${StringBuilder(myFixture.file.text).insert(pos, "/*caret*/")}`"
            }
        }
    }
}
