/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.intentions

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInsight.intention.IntentionActionDelegate
import com.intellij.openapiext.Testmark
import com.intellij.util.ui.UIUtil
import org.intellij.lang.annotations.Language
import org.rust.RsTestBase
import org.rust.fileTreeFromText
import kotlin.reflect.KClass
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.reflect.full.isSubclassOf

abstract class RsIntentionTestBase(private val intentionClass: KClass<out IntentionAction>) : RsTestBase() {

    protected val intention: IntentionAction
        get() = findIntention() ?: error("Failed to find `${intentionClass.simpleName}` intention")

    fun `test intention has documentation`() {
        if (!intentionClass.isSubclassOf(RsElementBaseIntentionAction::class)) return

        val directory = "intentionDescriptions/${intentionClass.simpleName}"
        val description = checkFileExists(Paths.get(directory, "description.html"))
        checkHtmlStyle(description)

        checkFileExists(Paths.get(directory, "before.rs.template"))
        checkFileExists(Paths.get(directory, "after.rs.template"))
    }

    private fun checkFileExists(path: Path): String = getResourceAsString(path.toString())
        ?: error("No ${path.fileName} found for $intentionClass ($path)")

    protected fun doAvailableTest(@Language("Rust") before: String, @Language("Rust") after: String) {
        InlineFile(before.trimIndent()).withCaret()
        launchAction()
        myFixture.checkResult(replaceCaretMarker(after.trimIndent()))
    }

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

    private fun launchAction() {
        UIUtil.dispatchAllInvocationEvents()
        myFixture.launchAction(intention)
    }

    protected fun doAvailableTest(@Language("Rust") before: String,
                                  @Language("Rust") after: String,
                                  testmark: Testmark) =
        testmark.checkHit { doAvailableTest(before, after) }

    protected fun doUnavailableTest(@Language("Rust") before: String) {
        InlineFile(before).withCaret()
        val intention = findIntention()
        check(intention == null) {
            "\"${intentionClass.simpleName}\" should not be available"
        }
    }

    private fun findIntention(): IntentionAction? {
        return myFixture.availableIntentions.firstOrNull {
            val originalIntention = IntentionActionDelegate.unwrap(it)
            intentionClass == originalIntention::class
        }
    }
}
