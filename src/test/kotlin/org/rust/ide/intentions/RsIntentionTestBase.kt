/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.intentions

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInsight.intention.IntentionActionDelegate
import com.intellij.codeInsight.intention.IntentionManager
import com.intellij.codeInsight.template.impl.TemplateManagerImpl
import com.intellij.openapi.util.TextRange
import com.intellij.util.PathUtil
import com.intellij.util.ui.UIUtil
import org.intellij.lang.annotations.Language
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.rust.*
import org.rust.ide.DeferredPreviewCheck
import org.rust.ide.checkNoPreview
import org.rust.ide.checkPreviewAndLaunchAction
import java.util.*
import kotlin.reflect.KClass
import kotlin.reflect.full.isSubclassOf

/**
 * A base test class for intention action tests.
 *
 * By default, each test declared in a subclass of this class will run several times - one per each
 * [TestWrapping] value returned from [RsIntentionTestBase.data] method. This allows to test intention
 * actions under different circumstances, e.g. inside a procedural macro call. Use [SkipTestWrapping]
 * annotation to skip test run with a specific (or all) [TestWrapping] (s).
 */
@RunWith(RsJUnit4ParameterizedTestRunner::class)
@Parameterized.UseParametersRunnerFactory(RsJUnit4ParameterizedTestRunner.RsRunnerForParameters.Factory::class)
abstract class RsIntentionTestBase(private val intentionClass: KClass<out IntentionAction>) : RsTestBase() {

    @field:Parameterized.Parameter(0)
    override lateinit var testWrapping: TestWrapping

    companion object {
        @Parameterized.Parameters(name = "{index}: {0}")
        @JvmStatic fun data(): Iterable<TestWrapping> = listOf(
            TestWrapping.NONE,
            TestWrapping.ATTR_MACRO_AS_IS_AT_CARET,
        )
    }

    protected val intention: IntentionAction
        get() = findIntention() ?: error("Failed to find `${intentionClass.simpleName}` intention")

    protected open val previewExpected: Boolean get() = intention.startInWriteAction()

    @SkipTestWrapping
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
        val previewChecker = launchAction(preview)
        this.testWrappingUnwrapper?.unwrap()
        myFixture.checkResult(replaceCaretMarker(after.trimIndent()))
        previewChecker.checkPreview()
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
        val previewChecker = launchAction()
        assertNotNull(TemplateManagerImpl.getTemplateState(myFixture.editor))
        myFixture.type(toType)
        assertNull(TemplateManagerImpl.getTemplateState(myFixture.editor))
        this.testWrappingUnwrapper?.unwrap()
        myFixture.checkResult(replaceCaretMarker(after.trimIndent()))
        previewChecker.checkPreview()
    }

    @Suppress("unused")
    protected fun doAvailableTestWithFileTree(
        @Language("Rust") fileStructureBefore: String,
        @Language("Rust") openedFileAfter: String
    ) {
        fileTreeFromText(fileStructureBefore).createAndOpenFileWithCaretMarker()
        val previewChecker = launchAction()
        this.testWrappingUnwrapper?.unwrap()
        myFixture.checkResult(replaceCaretMarker(openedFileAfter.trimIndent()))
        previewChecker.checkPreview()
    }

    protected fun doAvailableTestWithFileTreeComplete(
        @Language("Rust") fileStructureBefore: String,
        @Language("Rust") fileStructureAfter: String
    ) {
        fileTreeFromText(fileStructureBefore).createAndOpenFileWithCaretMarker()
        val previewChecker = launchAction()
        testWrappingUnwrapper?.unwrap()
        fileTreeFromText(replaceCaretMarker(fileStructureAfter)).check(myFixture)
        previewChecker.checkPreview()
    }

    protected fun launchAction(@Language("Rust") preview: String? = null): DeferredPreviewCheck {
        UIUtil.dispatchAllInvocationEvents()
        // Check preview only for intentions from Rust plugin
        return if (intentionClass.isSubclassOf(RsElementBaseIntentionAction::class)) {
            if (previewExpected) {
                val isWrappingActive = testWrappingUnwrapper != null
                myFixture.checkPreviewAndLaunchAction(intention, preview, isWrappingActive)
            } else {
                val previewChecker = myFixture.checkNoPreview(intention)
                myFixture.launchAction(intention)
                previewChecker
            }
        } else {
            myFixture.launchAction(intention)
            DeferredPreviewCheck.IgnorePreview
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
