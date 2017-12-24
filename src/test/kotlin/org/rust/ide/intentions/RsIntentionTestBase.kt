/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.intentions

import com.intellij.codeInsight.intention.IntentionAction
import org.intellij.lang.annotations.Language
import org.rust.fileTreeFromText
import org.rust.lang.RsTestBase
import org.rust.openapiext.Testmark

abstract class RsIntentionTestBase(val intention: IntentionAction) : RsTestBase() {
    fun `test intention has documentation`() {
        val directory = "intentionDescriptions/${intention.javaClass.simpleName}"
        val files = listOf("before.rs.template", "after.rs.template", "description.html")
        for (file in files) {
            val text = getResourceAsString("$directory/$file")
                ?: fail("No inspection description for ${intention.javaClass}.\n" +
                "Add ${files.joinToString()} to src/main/resources/$directory")

            if (file.endsWith(".html")) {
                checkHtmlStyle(text)
            }
        }
    }

    protected fun doAvailableTest(@Language("Rust") before: String, @Language("Rust") after: String) {
        InlineFile(before.trimIndent()).withCaret()
        myFixture.launchAction(intention)
        myFixture.checkResult(replaceCaretMarker(after.trimIndent()))
    }

    protected fun doAvailableTestWithFileTree(@Language("Rust") fileStructureBefore: String, @Language("Rust") openedFileAfter: String) {
        fileTreeFromText(fileStructureBefore).createAndOpenFileWithCaretMarker()
        myFixture.launchAction(intention)
        myFixture.checkResult(replaceCaretMarker(openedFileAfter.trimIndent()))
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
