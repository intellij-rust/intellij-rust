/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.completion

import org.intellij.lang.annotations.Language
import org.rust.ProjectDescriptor
import org.rust.WithDependencyRustProjectDescriptor

class RsMacroBracketCompletionTest : RsCompletionTestBase() {

    fun `test default bracket`() = doTest("""
        $MACRO_PLACEHOLDER
        fn main() {
            foo/*caret*/
        }
    """, """
        $MACRO_PLACEHOLDER
        fn main() {
            foo!(/*caret*/)
        }
    """)

    fun `test bracket from documentation`() = doTest("""
        /// `foo![]`
        ///
        /// ```
        /// foo![];
        /// bar_foo!();
        /// foo();
        /// ```
        ///
        /// `foo!()`
        ///
        $MACRO_PLACEHOLDER
        fn main() {
            foo/*caret*/
        }
    """, """
        /// `foo![]`
        ///
        /// ```
        /// foo![];
        /// bar_foo!();
        /// foo();
        /// ```
        ///
        /// `foo!()`
        ///
        $MACRO_PLACEHOLDER
        fn main() {
            foo![/*caret*/]
        }
    """)

    fun `test insert space for before braces`() = doTest("""
        /// `foo! {}`
        $MACRO_PLACEHOLDER
        fn main() {
            foo/*caret*/
        }
    """, """
        /// `foo! {}`
        $MACRO_PLACEHOLDER
        fn main() {
            foo! {/*caret*/}
        }
    """)

    fun `test raw macro calls`() = doTest("""
        /// `r#foo![]`
        ///
        /// ```
        /// foo!();
        /// foo![];
        /// ```
        ///
        $MACRO_PLACEHOLDER
        fn main() {
            foo/*caret*/
        }
    """, """
        /// `r#foo![]`
        ///
        /// ```
        /// foo!();
        /// foo![];
        /// ```
        ///
        $MACRO_PLACEHOLDER
        fn main() {
            foo![/*caret*/]
        }
    """)

    fun `test do not mess with other macro calls`() = doTest("""
        /// ```
        /// foo![];
        /// assert!(true);
        /// assert!(true);
        /// ```
        ///
        $MACRO_PLACEHOLDER
        fn main() {
            foo/*caret*/
        }
    """, """
        /// ```
        /// foo![];
        /// assert!(true);
        /// assert!(true);
        /// ```
        ///
        $MACRO_PLACEHOLDER
        fn main() {
            foo![/*caret*/]
        }
    """)

    @ProjectDescriptor(WithDependencyRustProjectDescriptor::class)
    fun `test bracket from documentation (proc macro)`() = doSingleCompletionByFileTree("""
    //- dep-proc-macro/lib.rs
        /// ```
        /// foo! {}
        /// ```
        #[proc_macro]
        pub fn foo(input: TokenStream) -> TokenStream { input }
    //- lib.rs
        fn main() {
            foo/*caret*/
        }
    """, """
        use dep_proc_macro::foo;

        fn main() {
            foo! {/*caret*/}
        }
    """)

    /**
     * Checks completion for both macro 1.0 and macro 2.0.
     * It substitutes [MACRO_PLACEHOLDER] with actual macro definition of macro `foo`
     */
    private fun doTest(@Language("Rust") before: String, @Language("Rust") after: String) {
        fun doTest(macroDefinition: String) {
            doSingleCompletion(
                before.replace(MACRO_PLACEHOLDER, macroDefinition),
                after.replace(MACRO_PLACEHOLDER, macroDefinition)
            )
        }

        // macro 1.0
        doTest("macro_rules! foo { () => {}; }")
        // macro 2.0
        doTest("macro foo() {}")
    }

    companion object {
        private const val MACRO_PLACEHOLDER = "%macro_definition%"
    }
}
