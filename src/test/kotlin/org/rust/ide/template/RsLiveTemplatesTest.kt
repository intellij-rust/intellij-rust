/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.template

import com.intellij.openapi.actionSystem.IdeActions
import org.intellij.lang.annotations.Language
import org.rust.RsTestBase

class RsLiveTemplatesTest : RsTestBase() {

    fun `test struct field`() = expandSnippet("""
        struct S {
            f/*caret*/
        }
    """, """
        struct S {
            foo: u32,
        }
    """)

    fun `test print`() = expandSnippet("""
        fn main() {
            p/*caret*/
        }
    """, """
        fn main() {
            println!("");
        }
    """)

    fun `test attribute`() = noSnippet("""
        #[macro/*caret*/]
        extern crate std;

        fn main() { }
    """)

    fun `test comment`() = noSnippet("""
        fn main() {
            // p/*caret*/
        }
    """)

    fun `test doc comment`() = noSnippet("""
        /// p/*caret*/
        fn f() {}
    """)

    fun `test string literal`() = noSnippet("""
        fn main() {
            let _ = "p/*caret*/";
        }
    """)

    fun `test raw string literal`() = noSnippet("""
        fn main() {
            let _ = r##"p/*caret*/"##;
        }
    """)

    fun `test byte string literal`() = noSnippet("""
        fn main() {
            let _ = b"p/*caret*/";
        }
    """)

    fun `test field expression`() = noSnippet("""
        fn main() {
            let _ = foo.p/*caret*/
        }
    """)

    fun `test method expression`() = noSnippet("""
        fn main() {
            let _ = foo.p/*caret*/()
        }
    """)

    fun `test path`() = noSnippet("""
        fn main() {
            let _ = foo::p/*caret*/
        }
    """)

    fun `test macro definition 1`() = noSnippet("""
        macro_rules! foo {
            (impl/*caret*/) => {};
        }
    """)

    fun `test macro definition 2`() = noSnippet("""
        macro_rules! foo {
            () => { impl/*caret*/ };
        }
    """)

    fun `test macro2 definition`() = noSnippet("""
        macro foo() {
            impl/*caret*/
        }
    """)

    fun `test macro call`() = noSnippet("""
        foo! {
            impl/*caret*/
        }
    """)

    val indent = "    "
    fun `test module level context available in file`() = expandSnippet("""
        tfn/*caret*/
    """, """
        #[test]
        fn /*caret*/() {
        $indent
        }
    """)

    fun `test module level context not available in function`() = noSnippet("""
        fn foo() {
            x.tfn/*caret*/
        }
    """)

    fun `test main is available in file`() = expandSnippet("""
        main/*caret*/
    """, """
        fn main() {
        $indent/*caret*/
        }
    """)

    fun `test impl`() = expandSnippet("""
        impl/*caret*/
    """, """
        impl /*caret*/ {
        $indent
        }
    """)

    fun `test implt`() = expandSnippet("""
        implt/*caret*/
    """, """
        impl /*caret*/ for  {
        $indent
        }
    """)

    private fun expandSnippet(@Language("Rust") before: String, @Language("Rust") after: String) =
        checkByText(before.trimIndent(), after.trimIndent()) {
            myFixture.performEditorAction(IdeActions.ACTION_EXPAND_LIVE_TEMPLATE_BY_TAB)
        }

    private fun noSnippet(@Language("Rust") code: String) = expandSnippet(code, code)
}
