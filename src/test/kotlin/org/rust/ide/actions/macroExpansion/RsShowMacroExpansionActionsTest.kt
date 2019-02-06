/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.actions.macroExpansion

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.project.Project
import org.intellij.lang.annotations.Language
import org.rust.RsTestBase
import org.rust.lang.core.macros.RsExpandedElement

class RsShowMacroExpansionActionsTest : RsTestBase() {

    fun `test no expansion available when macro is not under caret`() = testNoExpansionHappens("""
        fn main() {
            let a = /*caret*/boo();
            foo!();
        }
    """)

    fun `test single level expansion is performed by recursive action`() = testRecursiveExpansion("""
        macro_rules! foo {
            () => { println!(); }
        }
        /*caret*/foo!();
    """, """
        println!();
    """)

    fun `test single level expansion is performed by single step action`() = testSingleStepExpansion("""
            macro_rules! foo {
                () => { println!(); }
            }
            /*caret*/foo!();
        """, """
            println!();
        """)

    fun `test two leveled expansion is preformed with recursive expansion`() = testRecursiveExpansion("""
        macro_rules! boo {
            () => { println!(); }
        }

        macro_rules! foo {
            () => { boo!(); }
        }

        /*caret*/foo!();
    """, """
        println!();
    """)

    fun `test no second level expansion is preformed with single step expansion`() = testSingleStepExpansion("""
        macro_rules! boo {
            () => { println!(); }
        }

        macro_rules! foo {
            () => { boo!(); }
        }

        /*caret*/foo!();
    """, """
        boo!();
    """)

    fun `test macros correctly recursively expands to itself`() = testRecursiveExpansion("""
        macro_rules! foo {
            () => { println!(); }
            (${'$'}i: expr) => { foo!(); }
        }

        /*caret*/foo!(boo);
    """, """
        println!();
    """)

    fun `test that recursive expansion of macro without body is just its name`() = testMacroExpansionFail("""
            /*caret*/foo!();
        """, expandRecursively = true)

    fun `test that single step expansion of macro without body is just its name`() = testMacroExpansionFail("""
        /*caret*/foo!();
    """, expandRecursively = false)

    private fun testNoExpansionHappens(@Language("Rust") code: String) {
        InlineFile(code)

        val failingAction = object : RsShowMacroExpansionActionBase(expandRecursively = true) {
            override fun showExpansion(project: Project, editor: Editor, expansionDetails: MacroExpansionViewDetails) {
                fail("No expansion should be showed, but ${expansionDetails.expansion.elements.toText()} was shown!")
            }
        }

        failingAction.performForContext(dataContext)
    }

    private fun testRecursiveExpansion(@Language("Rust") code: String, @Language("Rust") expectedRaw: String) {
        testMacroExpansion(code, expectedRaw, expandRecursively = true)
    }

    private fun testSingleStepExpansion(@Language("Rust") code: String, @Language("Rust") expectedRaw: String) {
        testMacroExpansion(code, expectedRaw, expandRecursively = false)
    }

    private fun testMacroExpansion(
        @Language("Rust") code: String,
        @Language("Rust") expectedRaw: String,
        expandRecursively: Boolean
    ) {
        InlineFile(code)

        lateinit var expansions: List<RsExpandedElement>

        val action = object : RsShowMacroExpansionActionBase(expandRecursively = expandRecursively) {
            override fun showExpansion(project: Project, editor: Editor, expansionDetails: MacroExpansionViewDetails) {
                expansions = expansionDetails.expansion.elements
            }
        }

        action.performForContext(dataContext)

        val expandedMacroText = expansions.toText().removeWhitespace()
        val expected = expectedRaw.removeWhitespace()

        assertEquals(
            "${if (expandRecursively) "Recursive" else "Single step"} expansion went wrong!",
            expected,
            expandedMacroText
        )
    }

    private fun testMacroExpansionFail(
        @Language("Rust") code: String,
        expandRecursively: Boolean
    ) {
        InlineFile(code)

        var failed: Boolean = false

        val action = object : RsShowMacroExpansionActionBase(expandRecursively = expandRecursively) {
            override fun showExpansion(project: Project, editor: Editor, expansionDetails: MacroExpansionViewDetails) {
                error("Expected expansion fail, but got expansion: `${expansionDetails.expansion.elements.map { it.text }}`")
            }

            override fun showError(editor: Editor) {
                failed = true
            }
        }

        action.performForContext(dataContext)

        check(failed)
    }

    private val dataContext
        get() = (myFixture.editor as EditorEx).dataContext

}

private fun List<RsExpandedElement>.toText(): String =
    joinToString("\n") { it.text }

private fun String.removeWhitespace(): String =
    filter { !it.isWhitespace() }
