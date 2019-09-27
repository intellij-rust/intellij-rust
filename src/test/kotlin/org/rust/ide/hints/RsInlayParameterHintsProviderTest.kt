/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.hints

import com.intellij.openapi.vfs.VirtualFileFilter
import org.rust.fileTreeFromText
import org.rust.lang.core.psi.RsMethodCall

class RsInlayParameterHintsProviderTest : RsPlainInlayHintsProviderTestBase() {

    fun `test fn args`() = checkByText("""
        fn foo(arg: u32, arg2: u32) {}
        fn main() { foo(/*hint text="arg:"*/0, /*hint text="arg2:"*/1); }
    """, enabledHints = RsPlainParameterHint.PARAMETER_HINT)

    fun `test arg out of bounds`() = checkByText("""
        fn foo(arg: u32) {}
        fn main() { foo(/*hint text="arg:"*/0, 1); }
    """, enabledHints = RsPlainParameterHint.PARAMETER_HINT)

    fun `test method args`() = checkByText("""
        struct S;
        impl S {
            fn foo(self, arg: u32, arg2: u32) {}
        }
        fn main() {
            let s = S;
            s.foo(/*hint text="arg:"*/0, /*hint text="arg2:"*/1);
        }
    """, enabledHints = RsPlainParameterHint.PARAMETER_HINT)

    fun `test struct fn arg`() = checkByText("""
        struct S;
        impl S {
            fn foo(self, arg: u32) {}
        }
        fn main() {
            let s = S;
            S::foo(/*hint text="self:"*/s, /*hint text="arg:"*/0);
        }
    """, enabledHints = RsPlainParameterHint.PARAMETER_HINT)

    fun `test smart hint same parameter name`() = checkByText("""
        fn foo(arg: u32, arg2: u32) {}
        fn main() {
            let arg = 0;
            foo(arg, /*hint text="arg2:"*/1);
        }
    """, enabledHints = RsPlainParameterHint.PARAMETER_HINT)

    fun `test smart hint method start with set`() = checkByText("""
        struct S;
        impl S {
            fn set_foo(self, arg: u32) {}
        }
        fn main() {
            let s = S;
            s.set_foo(1);
        }
    """, enabledHints = RsPlainParameterHint.PARAMETER_HINT)

    fun `test smart hint self call start with set`() = checkByText("""
        struct S;
        impl S {
            fn set_foo(self, arg: u32) {}
        }
        fn main() {
            let s = S;
            S::set_foo(s, 0);
        }
    """, enabledHints = RsPlainParameterHint.PARAMETER_HINT)

    fun `test smart hint same function name and single parameter`() = checkByText("""
        fn foo(arg: u32) {}
        fn main() {
            let foo = 0;
            foo(foo);
        }
    """, enabledHints = RsPlainParameterHint.PARAMETER_HINT)

    fun `test smart hint parameter name and ref input`() = checkByText("""
        fn foo(arg: &u32) {}
        fn main() {
            let arg = 0;
            foo(&arg);
        }
    """, enabledHints = RsPlainParameterHint.PARAMETER_HINT)

    fun `test smart hint same method name and single parameter`() = checkByText("""
        struct S;
        impl S {
            fn foo(self, foo: u32) {}
        }
        fn main() {
            let s = S;
            s.foo(10);
        }
    """, enabledHints = RsPlainParameterHint.PARAMETER_HINT)

    fun `test smart hint same method name (self call) and single parameter`() = checkByText("""
        struct S;
        impl S {
            fn foo(self, foo: u32) {}
        }
        fn main() {
            let s = S;
            S::foo(s, 10);
        }
    """, enabledHints = RsPlainParameterHint.PARAMETER_HINT)

    fun `test smart should not annotate tuple structs`() = checkByText("""
        struct TS(i32, f32);
        fn main() {
            let s = TS(5i32, 10.0f32);
        }
    """, enabledHints = RsPlainParameterHint.PARAMETER_HINT)

    fun `test fn arg with mut ident`() = checkByText("""
        fn foo(mut arg: u32) {}
        fn main() { foo(/*hint text="arg:"*/0); }
    """, enabledHints = RsPlainParameterHint.PARAMETER_HINT)

    fun `test fn arg with mut array`() = checkByText("""
        fn foo([mut x, y]: [i32; 2]) {}
        fn main() { foo(/*hint text="[x, y]:"*/0); }
    """, enabledHints = RsPlainParameterHint.PARAMETER_HINT)

    fun `test fn arg with mut tuple`() = checkByText("""
        fn foo((mut x, y): (i32, i32)) {}
        fn main() { foo(/*hint text="(x, y):"*/0); }
    """, enabledHints = RsPlainParameterHint.PARAMETER_HINT)

    fun `test fn arg with mut struct`() = checkByText("""
        struct S { x: i32, y: i32 }
        fn foo(S { mut x, y }: S) {}
        fn main() { foo(/*hint text="S {x, y}:"*/0); }
    """, enabledHints = RsPlainParameterHint.PARAMETER_HINT)

    fun `test fn arg with mut tuple struct`() = checkByText("""
        struct S(i32, i32);
        fn foo(S(mut x, y): S) {}
        fn main() { foo(/*hint text="S(x, y):"*/0); }
    """, enabledHints = RsPlainParameterHint.PARAMETER_HINT)

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
}
