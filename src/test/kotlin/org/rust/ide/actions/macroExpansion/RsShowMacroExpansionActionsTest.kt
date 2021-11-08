/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.actions.macroExpansion

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.project.Project
import org.intellij.lang.annotations.Language
import org.rust.*
import org.rust.ide.experiments.RsExperiments.EVALUATE_BUILD_SCRIPTS
import org.rust.ide.experiments.RsExperiments.PROC_MACROS
import org.rust.lang.core.macros.MacroExpansionScope
import org.rust.lang.core.macros.RsExpandedElement
import org.rust.lang.core.macros.errors.GetMacroExpansionError

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
            ($ i: expr) => { foo!(); }
        }

        /*caret*/foo!(boo);
    """, """
        println!();
    """)

    fun `test dollar crate replacement`() = testSingleStepExpansion("""
        macro_rules! foo {
            () => { $ crate::foobar(); }
        }

        fn main() { /*caret*/foo!(); }
    """, """
        ::test_package::foobar();
    """)

    fun `test that recursive expansion of macro without body is just its name`() = testMacroExpansionFail("""
            /*caret*/foo!();
        """, expandRecursively = true)

    fun `test that single step expansion of macro without body is just its name`() = testMacroExpansionFail("""
        /*caret*/foo!();
    """, expandRecursively = false)

    @MinRustcVersion("1.37.0")
    @ProjectDescriptor(WithStdlibRustProjectDescriptor::class)
    fun `test recursive expansion does not expand compile_error!()`() = testRecursiveExpansion("""
        fn foo() {
            macro_rules! foo {
                () => { compile_error!(""); }
            }
            /*caret*/foo!();
        }
    """, """
        compile_error!("");
    """)

    @MinRustcVersion("1.46.0")
    @ExpandMacros(MacroExpansionScope.WORKSPACE)
    @WithExperimentalFeatures(EVALUATE_BUILD_SCRIPTS, PROC_MACROS)
    @ProjectDescriptor(WithProcMacroRustProjectDescriptor::class)
    fun `test attribute proc macro`() = testSingleStepExpansion("""
        use test_proc_macros::attr_as_is;

        #[/*caret*/attr_as_is]
        #[attr_as_is]
        fn foo() {}
    """, """
        #[attr_as_is]
        fn foo() {}
    """)

    @MinRustcVersion("1.46.0")
    @ExpandMacros(MacroExpansionScope.WORKSPACE)
    @WithExperimentalFeatures(EVALUATE_BUILD_SCRIPTS, PROC_MACROS)
    @ProjectDescriptor(WithProcMacroRustProjectDescriptor::class)
    fun `test recursive attribute proc macro`() = testRecursiveExpansion("""
        use test_proc_macros::attr_as_is;

        #[/*caret*/attr_as_is]
        #[attr_as_is]
        fn foo() {}
    """, """
        fn foo() {}
    """)

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

    private fun testSingleStepExpansion(
        @Language("Rust") code: String,
        @Language("Rust", prefix = "fn main(){", suffix = ";}") expectedRaw: String
    ) {
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

        var failed = false

        val action = object : RsShowMacroExpansionActionBase(expandRecursively = expandRecursively) {
            override fun showExpansion(project: Project, editor: Editor, expansionDetails: MacroExpansionViewDetails) {
                error("Expected expansion fail, but got expansion: `${expansionDetails.expansion.elements.map { it.text }}`")
            }

            override fun showError(editor: Editor, error: GetMacroExpansionError) {
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
