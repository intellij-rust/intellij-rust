/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.spellchecker

import com.intellij.spellchecker.inspections.SpellCheckingInspection
import org.intellij.lang.annotations.Language
import org.rust.lang.RsTestBase

class RsSpellCheckerTest : RsTestBase() {
    fun testComments() = doTest("""// Hello, <TYPO descr="Typo: In word 'Wodrl'">Wodrl</TYPO>!""")

    fun testStringLiterals() = doTest("""
        fn main() {
            let s = "Hello, <TYPO descr="Typo: In word 'Wodlr'">Wodlr</TYPO>!";
            let invalid_escape = "aadsds\z";
        }
    """)

    fun testCommentsSuppressed() = doTest("// Hello, Wodrl!", processComments = false)

    fun testStringLiteralsSuppressed() = doTest("""
        fn main() {
            let s = "Hello, Wodlr!";
        }
    """, processLiterals = false)

    fun testStringLiteralsWithEscapes() = doTest("""
        fn main() {
            let s = "Hello, <TYPO>W\u{6F}dlr</TYPO>!";
            let s = "Hello, <TYPO>W\x6Fdlr</TYPO>!";
        }
    """)

    private fun doTest(@Language("Rust") text: String, processComments: Boolean = true, processLiterals: Boolean = true) {
        val inspection = SpellCheckingInspection()
        inspection.processLiterals = processLiterals
        inspection.processComments = processComments

        myFixture.configureByText("main.rs", text)
        myFixture.enableInspections(inspection)
        myFixture.testHighlighting(false, false, true, "main.rs")
    }
}
