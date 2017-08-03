/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.hints

import com.intellij.psi.PsiElement
import org.assertj.core.api.Assertions
import org.intellij.lang.annotations.Language
import org.rust.lang.RsTestBase
import org.rust.lang.core.psi.RsCallExpr
import org.rust.lang.core.psi.RsDotExpr
import org.rust.lang.core.psi.RsLambdaExpr
import org.rust.lang.core.psi.RsLetDecl
import org.rust.lang.core.psi.RsMethodCall


class RsInlayParameterHintsProviderTest : RsTestBase() {

    fun testFnOneArg() = checkByText<RsCallExpr>("""
        fn foo(arg: u32) {}
        fn main() { foo(/*caret*/0); }
                    //^
    """, "arg:", 0)

    fun testFnTwoArg() = checkByText<RsCallExpr>("""
        fn foo(arg: u32, arg2: u32) {}
        fn main() { foo(0, /*caret*/1); }
                    //^
    """, "arg2:", 1)

    fun `test arg out of bounds`() = checkByText<RsCallExpr>("""
        fn foo(arg: u32) {}
        fn main() { foo(0, /*caret*/1); }
                    //^
    """, "<none>", -1)

    fun testMethodTwoArg() = checkByText<RsMethodCall>("""
        struct S;
        impl S {
            fn foo(self, arg: u32, arg2: u32) {}
        }
        fn main() {
            let s = S;
            s.foo(0, /*caret*/1);
        }    //^
    """, "arg2:", 1)

    fun testStructFnArg() = checkByText<RsCallExpr>("""
        struct S;
        impl S {
            fn foo(self, arg: u32) {}
        }
        fn main() {
            let s = S;
            S::foo(s, /*caret*/0);
        }    //^
    """, "arg:", 1)

    fun testLetDecl() = checkByText<RsLetDecl>("""
        struct S;
        fn main() {
            let s/*caret*/ = S;
        }  //^
    """, ": S", 0)

    fun `test smart hint same parameter name`() = checkByText<RsCallExpr>("""
        fn foo(arg: u32, arg2: u32) {}
        fn main() {
            let arg = 0;
            foo(arg, /*caret*/1);
        } //^
    """, "arg2:", 0)

    fun `test smart hint method start with set`() = checkNoHint<RsMethodCall>("""
        struct S;
        impl S {
            fn set_foo(self, arg: u32) {}
        }
        fn main() {
            let s = S;
            s.set_foo(1);
        }     //^
    """)

    fun `test smart hint self call start with set`() = checkNoHint<RsCallExpr>("""
        struct S;
        impl S {
            fn set_foo(self, arg: u32) {}
        }
        fn main() {
            let s = S;
            S::set_foo(s, /*caret*/0);
        } //^
    """)

    fun `test smart hint same function name and single parameter`() = checkNoHint<RsCallExpr>("""
        fn foo(arg: u32) {}
        fn main() {
            let foo = 0;
            foo(foo);
        } //^
    """)

    fun `test smart hint parameter name and ref input`() = checkNoHint<RsCallExpr>("""
        fn foo(arg: &u32) {}
        fn main() {
            let arg = 0;
            foo(&arg);
        } //^
    """)

    fun `test smart hint same method name and single parameter`() = checkNoHint<RsMethodCall>("""
        struct S;
        impl S {
            fn foo(self, foo: u32) {}
        }
        fn main() {
            let s = S;
            s.foo(10);
        }    //^
    """)

    fun `test smart hint same method name (self call) and single parameter`() = checkNoHint<RsCallExpr>("""
        struct S;
        impl S {
            fn foo(self, foo: u32) {}
        }
        fn main() {
            let s = S;
            S::foo(s, 10);
        }    //^
    """)

    fun `test smart should not annotate tuples`() = checkNoHint<RsCallExpr>("""
        enum Option<T> {
            Some(T),
            None
        }
        fn main() {
            let s = Option::Some(10);
        }                      //^
    """)

    fun `test lamdba type hint`() = checkByText<RsLambdaExpr>("""
        #[lang = "fn_once"]
        trait FnOnce<Args> { type Output; }

        #[lang = "fn_mut"]
        trait FnMut<Args>: FnOnce<Args> { }

        #[lang = "fn"]
        trait Fn<Args>: FnMut<Args> { }
        struct S;
        fn with_s<F: Fn(S)>(f: F) {}
        fn main() {
            with_s(|s/*caret*/| s.bar())
        }         //^
    """, ": S", 0)

    inline private fun <reified T : PsiElement> checkNoHint(@Language("Rust") code: String) {
        InlineFile(code)
        val handler = RsInlayParameterHintsProvider()
        val target = findElementInEditor<T>("^")
        val inlays = handler.getParameterHints(target)
        Assertions.assertThat(inlays.size).isEqualTo(0)
    }

    inline private fun <reified T : PsiElement> checkByText(@Language("Rust") code: String, hint: String, pos: Int) {
        InlineFile(code)
        val target = findElementInEditor<T>("^")
        val inlays = RsInlayParameterHintsProvider().getParameterHints(target)
        if (pos != -1) {
            check(pos < inlays.size) {
                "Expected at least ${pos + 1} hints, got ${inlays.map { it.text }}"
            }
            Assertions.assertThat(inlays[pos].text).isEqualTo(hint)
            Assertions.assertThat(inlays[pos].offset).isEqualTo(myFixture.editor.caretModel.offset)
        }
    }
}
