/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.hints

import com.intellij.codeInsight.daemon.impl.HintRenderer
import com.intellij.openapi.vfs.VirtualFileFilter
import org.intellij.lang.annotations.Language
import org.rust.ProjectDescriptor
import org.rust.RsTestBase
import org.rust.WithStdlibRustProjectDescriptor
import org.rust.fileTreeFromText
import org.rust.lang.core.psi.RsMethodCall

class RsInlayParameterHintsProviderTest : RsTestBase() {

    fun `test fn args`() = checkByText("""
        fn foo(arg: u32, arg2: u32) {}
        fn main() { foo(/*hint text="arg:"*/0, /*hint text="arg2:"*/1); }
    """, enabledHints = HintType.PARAMETER_HINT)

    fun `test arg out of bounds`() = checkByText("""
        fn foo(arg: u32) {}
        fn main() { foo(/*hint text="arg:"*/0, 1); }
    """, enabledHints = HintType.PARAMETER_HINT)

    fun `test method args`() = checkByText("""
        struct S;
        impl S {
            fn foo(self, arg: u32, arg2: u32) {}
        }
        fn main() {
            let s = S;
            s.foo(/*hint text="arg:"*/0, /*hint text="arg2:"*/1);
        }
    """, enabledHints = HintType.PARAMETER_HINT)

    fun `test struct fn arg`() = checkByText("""
        struct S;
        impl S {
            fn foo(self, arg: u32) {}
        }
        fn main() {
            let s = S;
            S::foo(/*hint text="self:"*/s, /*hint text="arg:"*/0);
        }
    """, enabledHints = HintType.PARAMETER_HINT)

    fun `test let decl`() = checkByText("""
        struct S;
        fn main() {
            let s/*hint text=": S"*/ = S;
        }
    """, enabledHints = HintType.LET_BINDING_HINT, smart = false)

    fun `test let stmt without expression`() = checkByText("""
        struct S;
        fn main() {
            let s/*hint text=": S"*/;
            s = S;
        }
    """, enabledHints = HintType.LET_BINDING_HINT, smart = false)

    fun `test no redundant hints`() = checkByText("""
        fn main() {
            let _ = 1;
            let _a = 1;
            let a = UnknownType;
        }
    """, enabledHints = HintType.LET_BINDING_HINT, smart = false)

    fun `test smart hint don't show redundant hints`() = checkByText("""
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
    """, enabledHints = HintType.LET_BINDING_HINT)

    fun `test let decl tuple`() = checkByText("""
        struct S;
        fn main() {
            let (s/*hint text=": S"*/, c/*hint text=": S"*/) = (S, S);
        }
    """, enabledHints = HintType.LET_BINDING_HINT)

    fun `test pat field`() = checkByText("""
        struct S;
        struct TupleStruct(S);
        struct BracedStruct { a: S, b: S }
        fn main() {
            let TupleStruct(x/*hint text=": S"*/) = TupleStruct(S);
            let BracedStruct { a: a/*hint text=": S"*/, b/*hint text=": S"*/ } = BracedStruct { a: S, b: S };
        }
    """, enabledHints = HintType.LET_BINDING_HINT, smart = false)

    fun `test smart hint same parameter name`() = checkByText("""
        fn foo(arg: u32, arg2: u32) {}
        fn main() {
            let arg = 0;
            foo(arg, /*hint text="arg2:"*/1);
        }
    """, enabledHints = HintType.PARAMETER_HINT)

    fun `test smart hint method start with set`() = checkByText("""
        struct S;
        impl S {
            fn set_foo(self, arg: u32) {}
        }
        fn main() {
            let s = S;
            s.set_foo(1);
        }
    """, enabledHints = HintType.PARAMETER_HINT)

    fun `test smart hint self call start with set`() = checkByText("""
        struct S;
        impl S {
            fn set_foo(self, arg: u32) {}
        }
        fn main() {
            let s = S;
            S::set_foo(s, 0);
        }
    """, enabledHints = HintType.PARAMETER_HINT)

    fun `test smart hint same function name and single parameter`() = checkByText("""
        fn foo(arg: u32) {}
        fn main() {
            let foo = 0;
            foo(foo);
        }
    """, enabledHints = HintType.PARAMETER_HINT)

    fun `test smart hint parameter name and ref input`() = checkByText("""
        fn foo(arg: &u32) {}
        fn main() {
            let arg = 0;
            foo(&arg);
        }
    """, enabledHints = HintType.PARAMETER_HINT)

    fun `test smart hint same method name and single parameter`() = checkByText("""
        struct S;
        impl S {
            fn foo(self, foo: u32) {}
        }
        fn main() {
            let s = S;
            s.foo(10);
        }
    """, enabledHints = HintType.PARAMETER_HINT)

    fun `test smart hint same method name (self call) and single parameter`() = checkByText("""
        struct S;
        impl S {
            fn foo(self, foo: u32) {}
        }
        fn main() {
            let s = S;
            S::foo(s, 10);
        }
    """, enabledHints = HintType.PARAMETER_HINT)

    fun `test smart should not annotate tuples`() = checkByText("""
        enum Option<T> {
            Some(T),
            None
        }
        fn main() {
            let s = Option::Some(10);
        }
    """, enabledHints = HintType.LET_BINDING_HINT)

    fun `test smart should not annotate tuple structs`() = checkByText("""
        struct TS(i32, f32);
        fn main() {
            let s = TS(5i32, 10.0f32);
        }
    """, enabledHints = HintType.PARAMETER_HINT)

    private val fnTypes = """
        #[lang = "fn_once"]
        trait FnOnce<Args> { type Output; }

        #[lang = "fn_mut"]
        trait FnMut<Args>: FnOnce<Args> { }

        #[lang = "fn"]
        trait Fn<Args>: FnMut<Args> { }
    """

    fun `test lambda type hint`() = checkByText("""
        $fnTypes
        struct S;
        fn with_s<F: Fn(S)>(f: F) {}
        fn main() {
            with_s(|s/*hint text=": S"*/| s.bar())
        }
    """, enabledHints = HintType.LAMBDA_PARAMETER_HINT)

    fun `test lambda type not shown if redundant`() = checkByText("""
        $fnTypes
        struct S;
        fn with_s<F: Fn(S)>(f: F) {}
        fn main() {
            with_s(|s: S| s.bar())
            with_s(|_| ())
        }
    """, enabledHints = HintType.LAMBDA_PARAMETER_HINT)

    fun `test lambda type should show after an defined type correct`() = checkByText("""
        $fnTypes
        struct S;
        fn foo<T: Fn(S, S, (S, S)) -> ()>(action: T) {}
        fn main() {
            foo(|x/*hint text=": S"*/, y: S, z/*hint text=": (S, S)"*/| {});
        }
    """, enabledHints = HintType.LAMBDA_PARAMETER_HINT)

    fun `test don't render horrendous types in their full glory`() = checkByText("""
        struct S<T, U>;

        impl<T, U> S<T, U> {
            fn wrap<F>(self, f: F) -> S<F, Self> {
                unimplemented!()
            }
        }

        fn main() {
            let s: S<(), ()> = unimplemented!();
            let foo/*hint text=": S<fn(i32) -> i32, S<fn(i32) -> i32, S<…, …>>>"*/ = s
                .wrap(|x: i32| x)
                .wrap(|x: i32| x)
                .wrap(|x: i32| x)
                .wrap(|x: i32| x);
        }
    """, enabledHints = HintType.LET_BINDING_HINT)

    @ProjectDescriptor(WithStdlibRustProjectDescriptor::class)
    fun `test inlay hint for loops`() = checkByText("""
        struct S;
        struct I;
        impl Iterator for I {
            type Item = S;
            fn next(&mut self) -> Option<S> { None }
        }

        fn main() {
            for s/*hint text=": S"*/ in I { }
        }
    """, enabledHints = HintType.FOR_PARAMETER_HINT)

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

    fun `test hints in if let expr`() = checkByText("""
        enum Option<T> {
            Some(T), None
        }
        fn main() {
            let result = Option::Some((1, 2));
            if let Option::Some((x/*hint text=": i32"*/, y/*hint text=": i32"*/)) = result {}
        }
    """, enabledHints = HintType.LET_BINDING_HINT)

    fun `test hints in if let expr with multiple patterns`() = checkByText("""
        enum V<T> {
            V1(T), V2(T)
        }
        fn main() {
            let result = V::V1((1, 2));
            if let V::V1(x/*hint text=": (i32, i32)"*/) | V::V2(x/*hint text=": (i32, i32)"*/) = result {}
        }
    """, enabledHints = HintType.LET_BINDING_HINT)

    fun `test hints in while let expr`() = checkByText("""
        enum Option<T> {
            Some(T), None
        }
        fn main() {
            let result = Option::Some((1, 2));
            while let Option::Some((x/*hint text=": i32"*/, y/*hint text=": i32"*/)) = result {}
        }
    """, enabledHints = HintType.LET_BINDING_HINT)

    fun `test hints in while let expr with multiple patterns`() = checkByText("""
        enum V<T> {
            V1(T), V2(T)
        }
        fn main() {
            let result = V::V1((1, 2));
            while let V::V1(x/*hint text=": (i32, i32)"*/) | V::V2(x/*hint text=": (i32, i32)"*/) = result {}
        }
    """, enabledHints = HintType.LET_BINDING_HINT)

    fun `test hints in match expr`() = checkByText("""
        enum Option<T> {
            Some(T), None
        }
        fn main() {
            let result = Option::Some((1, 2));
            match result {
                Option::Some((x/*hint text=": i32"*/, y/*hint text=": i32"*/)) => (),
                _ => ()
            }
        }
    """, enabledHints = HintType.LET_BINDING_HINT)

    fun `test show hints only for new local variables and ignore enum variants`() = checkByText("""
        enum Option<T> {
            Some(T), None
        }

        use Option::{Some, None};

        fn main() {
            let result = Some(1);
            match result {
                None => (),
                Name/*hint text=": Option<i32>"*/ => ()
            }
        }
    """, enabledHints = HintType.LET_BINDING_HINT)

    fun `test show hints for inner pat bindings`() = checkByText("""
        enum Option<T> {
            Some(T), None
        }

        use Option::{Some, None};

        fn main() {
            match Option::Some((1, 2)) {
                Some((x/*hint text=": i32"*/, 5)) => (),
                y => ()
            }
        }
    """, enabledHints = HintType.LET_BINDING_HINT)

    private fun checkByText(
        @Language("Rust") code: String,
        enabledHints: HintType? = null,
        smart: Boolean = true
    ) {
        InlineFile(code.replace(HINT_COMMENT_PATTERN, "<$1/>"))
        if (enabledHints != null) {
            for (hintType in HintType.values()) {
                hintType.option.set(hintType == enabledHints)
            }
        }
        HintType.SMART_HINTING.set(smart)

        try {
            myFixture.testInlays({ (it.renderer as HintRenderer).text }) { it.renderer is HintRenderer }
        } finally {
            HintType.values().forEach { it.option.set(true) }
            HintType.SMART_HINTING.set(true)
        }
    }

    companion object {
        private val HINT_COMMENT_PATTERN = Regex("""/\*(hint.*?)\*/""")
    }
}
