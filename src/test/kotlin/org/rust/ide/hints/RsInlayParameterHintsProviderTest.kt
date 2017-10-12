/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.hints

import com.intellij.openapi.vfs.VirtualFileFilter
import com.intellij.psi.PsiElement
import org.intellij.lang.annotations.Language
import org.rust.fileTreeFromText
import org.rust.lang.RsTestBase
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.descendantsOfType


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
    """, ": S", 0, smart = false)

    fun `test smart hint don't show redundant hints`() = checkNoHint<RsLetDecl>("""
        struct S;
        struct TupleStruct(f32);
        struct BracedStruct { f: f32 }
        enum E {
            C, B { f: f32 }, T(f32)
        }

        fn main() {
            let no_hint = S;
            let no_hint = TupleStruct(1.0);
            let no_hint = BracedStruct { f: 1.0 };
            let no_hint = E::C;
            let no_hint = E::B { f: 1.0 };
            let no_hint = E::T(1.0);
        }
    """)

    fun `test let decl tuple`() = checkByText<RsLetDecl>("""
        struct S;
        fn main() {
            let (s/*caret*/,c) = (S,S);
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
        }
    """)

    fun `test smart hint self call start with set`() = checkNoHint<RsCallExpr>("""
        struct S;
        impl S {
            fn set_foo(self, arg: u32) {}
        }
        fn main() {
            let s = S;
            S::set_foo(s, /*caret*/0);
        }
    """)

    fun `test smart hint same function name and single parameter`() = checkNoHint<RsCallExpr>("""
        fn foo(arg: u32) {}
        fn main() {
            let foo = 0;
            foo(foo);
        }
    """)

    fun `test smart hint parameter name and ref input`() = checkNoHint<RsCallExpr>("""
        fn foo(arg: &u32) {}
        fn main() {
            let arg = 0;
            foo(&arg);
        }
    """)

    fun `test smart hint same method name and single parameter`() = checkNoHint<RsMethodCall>("""
        struct S;
        impl S {
            fn foo(self, foo: u32) {}
        }
        fn main() {
            let s = S;
            s.foo(10);
        }
    """)

    fun `test smart hint same method name (self call) and single parameter`() = checkNoHint<RsCallExpr>("""
        struct S;
        impl S {
            fn foo(self, foo: u32) {}
        }
        fn main() {
            let s = S;
            S::foo(s, 10);
        }
    """)

    fun `test smart should not annotate tuples`() = checkNoHint<RsCallExpr>("""
        enum Option<T> {
            Some(T),
            None
        }
        fn main() {
            let s = Option::Some(10);
        }
    """)

    fun `test lambda type hint`() = checkByText<RsLambdaExpr>("""
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

    fun `test don't render horrendous types in their full glory`() = checkByText<RsLetDecl>("""
        struct S<T, U>;

        impl<T, U> S<T, U> {
            fn wrap<F>(self, f: F) -> S<F, Self> {
                unimplemented!()
            }
        }

        fn main() {
            let s: S<(), ()> = unimplemented!();
            let foo/*caret*/ = s
                .wrap(|x: i32| x)
                .wrap(|x: i32| x)
                .wrap(|x: i32| x)
                .wrap(|x: i32| x);
               //^
        }
    """, ": S<fn(i32) -> i32, S<fn(i32) -> i32, S<_, _>>>", 0)

    fun `test inlay hint for loops`() = checkByText<RsForExpr>("""
        trait Iterator { type Item; fn next(&mut self) -> Option<Self::Item>; }
        struct S;
        struct I;
        impl Iterator for I {
            type Item = S;
            fn next(&mut self) -> Option<S> { None }
        }

        fn main() {
            for s/*caret*/ in I { }
        }  //^
    """, ": S", 0)

    fun `test don't touch ast`() {
        fileTreeFromText("""
        //- main.rs
            mod foo;
            use foo::Foo;

            fn main() {
                Foo.bar(92)
            }     //^
        //- foo.rs
            struct Foo;
            impl Foo { fn bar(&self, x: i32) {} }
        """).createAndOpenFileWithCaretMarker()

        val handler = RsInlayParameterHintsProvider()
        val target = findElementInEditor<RsMethodCall>("^")
        checkAstNotLoaded(VirtualFileFilter.ALL)
        val inlays = handler.getParameterHints(target)
        check(inlays.size == 1)
    }

    inline private fun <reified T : PsiElement> checkNoHint(@Language("Rust") code: String, smart: Boolean = true) {
        InlineFile(code)
        HintType.SMART_HINTING.set(smart)
        val handler = RsInlayParameterHintsProvider()
        val targets = myFixture.file.descendantsOfType<T>()
        targets
            .map { handler.getParameterHints(it) }
            .forEach { check(it.isEmpty()) }
    }

    inline private fun <reified T : PsiElement> checkByText(@Language("Rust") code: String, hint: String, pos: Int, smart: Boolean = true) {
        InlineFile(code)
        HintType.SMART_HINTING.set(smart)
        val target = findElementInEditor<T>("^")
        val inlays = RsInlayParameterHintsProvider().getParameterHints(target)
        if (pos != -1) {
            check(pos < inlays.size) {
                "Expected at least ${pos + 1} hints, got ${inlays.map { it.text }}"
            }
            check(inlays[pos].text == hint)
            check(inlays[pos].offset == myFixture.editor.caretModel.offset)
        }
    }
}
