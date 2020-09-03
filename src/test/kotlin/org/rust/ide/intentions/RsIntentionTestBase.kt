/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.intentions

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.openapiext.Testmark
import com.intellij.util.ui.UIUtil
import org.intellij.lang.annotations.Language
import org.rust.RsTestBase
import org.rust.fileTreeFromText
import java.nio.file.Path
import java.nio.file.Paths

abstract class RsIntentionTestBase(val intention: IntentionAction) : RsTestBase() {

    fun `test intention has documentation`() {
        if (intention !is RsElementBaseIntentionAction<*>) return

        val directory = "intentionDescriptions/${intention.javaClass.simpleName}"
        val description = checkFileExists(intention, Paths.get(directory, "description.html"))
        checkHtmlStyle(description)

        checkFileExists(intention, Paths.get(directory, "before.rs.template"))
        checkFileExists(intention, Paths.get(directory, "after.rs.template"))
    }

    private fun checkFileExists(intention: IntentionAction, path: Path): String = getResourceAsString(path.toString())
        ?: error("No ${path.fileName} found for ${intention.javaClass} ($path)")

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
        check(intention.familyName !in myFixture.availableIntentions.mapNotNull { it.familyName }) {
            "\"$intention\" intention should not be available"
        }
    }
}
