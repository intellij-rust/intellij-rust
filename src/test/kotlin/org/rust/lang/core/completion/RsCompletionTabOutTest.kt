/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.completion

import com.intellij.openapi.actionSystem.IdeActions
import org.intellij.lang.annotations.Language
import org.rust.RsTestBase

class RsCompletionTabOutTest : RsTestBase() {

    fun `test type open brace`() {
        configureByText("""
            fn main() {
                func/*caret*/
            }
        """)
        type("(")
        tabOut()
        checkResult("""
            fn main() {
                func()/*caret*/
            }
        """)
    }

    fun `test type open brace and quotes`() {
        configureByText("""
            fn main() {
                func/*caret*/
            }
        """)
        type("(")
        type("\"")
        tabOut()
        tabOut()
        checkResult("""
            fn main() {
                func("")/*caret*/
            }
        """)
    }

    fun `test complete function`() = doTestCompleteTabOut("""
        fn main() {
            fu/*caret*/
        }
        fn func(s: i32) {}
    """, """
        fn main() {
            func()/*caret*/
        }
        fn func(s: i32) {}
    """)

    fun `test complete function and variable`() {
        configureByText("""
            fn main() {
                let foo = 1;
                fu/*caret*/
            }
            fn func(s: i32) {}
        """)
        complete()
        type("fo")
        complete()
        tabOut()
        checkResult("""
            fn main() {
                let foo = 1;
                func(foo)/*caret*/
            }
            fn func(s: i32) {}
        """)
    }

    fun `test complete macro`() = doTestCompleteTabOut("""
        macro_rules! gen { () => {} }
        fn main() {
            ge/*caret*/
        }
    """, """
        macro_rules! gen { () => {} }
        fn main() {
            gen!()/*caret*/
        }
    """)

    fun `test complete generic struct`() = doTestCompleteTabOut("""
        struct Foo<T> { t: T }
        fn func(_: Fo/*caret*/) {}
    """, """
        struct Foo<T> { t: T }
        fn func(_: Foo<>/*caret*/) {}
    """)

    fun `test complete enum with block fields`() = doTestCompleteTabOut("""
        enum E { AAA {} }
        fn main() {
            E::AA/*caret*/
        }
    """, """
        enum E { AAA {} }
        fn main() {
            E::AAA {}/*caret*/
        }
    """)

    fun `test complete enum with tuple fields`() = doTestCompleteTabOut("""
        enum E { AAA() }
        fn main() {
            E::AA/*caret*/
        }
    """, """
        enum E { AAA() }
        fn main() {
            E::AAA()/*caret*/
        }
    """)

    private fun type(s: String) {
        myFixture.type(s)
    }

    private fun complete() {
        myFixture.completeBasic()
    }

    private fun tabOut() {
        myFixture.performEditorAction(IdeActions.ACTION_BRACE_OR_QUOTE_OUT)
    }

    override fun configureByText(@Language("Rust") text: String) {
        InlineFile(text.trimIndent()).withCaret()
    }

    private fun checkResult(@Language("Rust") code: String) {
        check("/*caret*/" in code)
        myFixture.checkResult(replaceCaretMarker(code.trimIndent()))
    }

    private fun doTestCompleteTabOut(@Language("Rust") before: String, @Language("Rust") after: String) {
        configureByText(before)
        complete()
        tabOut()
        checkResult(after)
    }
}
