/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.hints.parameter

import com.intellij.codeInsight.daemon.impl.HintRenderer
import com.intellij.openapi.vfs.VirtualFileFilter
import org.intellij.lang.annotations.Language
import org.rust.MockAdditionalCfgOptions
import org.rust.RsTestBase
import org.rust.fileTreeFromText
import org.rust.lang.core.psi.RsMethodCall

class RsInlayParameterHintsProviderTest : RsTestBase() {
    fun `test fn args`() = checkByText("""
        fn foo(arg: u32, arg2: u32) {}
        fn main() { foo(/*hint text="arg:"*/0, /*hint text="arg2:"*/1); }
    """)

    fun `test arg out of bounds`() = checkByText("""
        fn foo(arg: u32) {}
        fn main() { foo(/*hint text="arg:"*/0, 1); }
    """)

    @MockAdditionalCfgOptions("intellij_rust")
    fun `test fn args with cfg`() = checkByText("""
        fn foo(
            #[cfg(not(intellij_rust))] arg1: u16,
            #[cfg(intellij_rust)]      arg2: u32,
            arg3: u64,
        ) {}
        fn main() { foo(/*hint text="arg2:"*/0, /*hint text="arg3:"*/1); }
    """)

    fun `test method args`() = checkByText("""
        struct S;
        impl S {
            fn foo(self, arg: u32, arg2: u32) {}
        }
        fn main() {
            let s = S;
            s.foo(/*hint text="arg:"*/0, /*hint text="arg2:"*/1);
        }
    """)

    fun `test struct fn arg`() = checkByText("""
        struct S;
        impl S {
            fn foo(self, arg: u32) {}
        }
        fn main() {
            let s = S;
            S::foo(/*hint text="self:"*/s, /*hint text="arg:"*/0);
        }
    """)

    fun `test smart hint same parameter name`() = checkByText("""
        fn foo(arg: u32, arg2: u32) {}
        fn main() {
            let arg = 0;
            foo(arg, /*hint text="arg2:"*/1);
        }
    """)

    fun `test smart hint method start with set`() = checkByText("""
        struct S;
        impl S {
            fn set_foo(self, arg: u32) {}
        }
        fn main() {
            let s = S;
            s.set_foo(1);
        }
    """)

    fun `test smart hint self call start with set`() = checkByText("""
        struct S;
        impl S {
            fn set_foo(self, arg: u32) {}
        }
        fn main() {
            let s = S;
            S::set_foo(s, 0);
        }
    """)

    fun `test smart hint same function name and single parameter`() = checkByText("""
        fn foo(arg: u32) {}
        fn main() {
            let foo = 0;
            foo(foo);
        }
    """)

    fun `test smart hint parameter name and ref input`() = checkByText("""
        fn foo(arg: &u32) {}
        fn main() {
            let arg = 0;
            foo(&arg);
        }
    """)

    fun `test smart hint same method name and single parameter`() = checkByText("""
        struct S;
        impl S {
            fn foo(self, foo: u32) {}
        }
        fn main() {
            let s = S;
            s.foo(10);
        }
    """)

    fun `test smart hint same method name (self call) and single parameter`() = checkByText("""
        struct S;
        impl S {
            fn foo(self, foo: u32) {}
        }
        fn main() {
            let s = S;
            S::foo(s, 10);
        }
    """)

    fun `test smart should not annotate tuple structs`() = checkByText("""
        struct TS(i32, f32);
        fn main() {
            let s = TS(5i32, 10.0f32);
        }
    """)

    fun `test smart should not annotate single lambda argument`() = checkByText("""
        fn foo(bar: impl Fn(i32) -> i32) {}
        fn main() {
            foo(|x| x);
        }
    """)

    fun `test fn arg with mut ident`() = checkByText("""
        fn foo(mut arg: u32) {}
        fn main() { foo(/*hint text="arg:"*/0); }
    """)

    fun `test fn arg with mut array`() = checkByText("""
        fn foo([mut x, y]: [i32; 2]) {}
        fn main() { foo(/*hint text="[x, y]:"*/0); }
    """)

    fun `test fn arg with mut tuple`() = checkByText("""
        fn foo((mut x, y): (i32, i32)) {}
        fn main() { foo(/*hint text="(x, y):"*/0); }
    """)

    fun `test fn arg with mut struct`() = checkByText("""
        struct S { x: i32, y: i32 }
        fn foo(S { mut x, y }: S) {}
        fn main() { foo(/*hint text="S {x, y}:"*/0); }
    """)

    fun `test fn arg with mut tuple struct`() = checkByText("""
        struct S(i32, i32);
        fn foo(S(mut x, y): S) {}
        fn main() { foo(/*hint text="S(x, y):"*/0); }
    """)

    fun `test generic enum variant single parameter smart mode disabled`() = checkByText("""
        enum Result<T, E> {
            Ok(T),
            Err(E)
        }

        fn main() {
            Result::Ok(/*hint text="T:"*/0);
            Result::Err(/*hint text="E:"*/0);
        }
    """, smart = false)

    fun `test generic enum variant single parameter smart mode enabled`() = checkByText("""
        enum Result<T, E> {
            Ok(T),
            Err(E)
        }

        fn main() {
            Result::Ok(0);
            Result::Err(0);
        }
    """, smart = true)

    fun `test generic enum variant multiple parameters smart mode disabled`() = checkByText("""
        enum Foo<T, E> {
            Bar(T, E),
        }

        fn main() {
            Foo::Bar(/*hint text="T:"*/0, /*hint text="E:"*/0);
        }
    """, smart = false)

    fun `test generic enum variant multiple parameters smart mode enabled`() = checkByText("""
        enum Foo<T, E> {
            Bar(T, E),
        }

        fn main() {
            Foo::Bar(/*hint text="T:"*/0, /*hint text="E:"*/0);
        }
    """, smart = true)

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

    @Suppress("UnstableApiUsage")
    private fun checkByText(@Language("Rust") code: String, smart: Boolean = true) {
        InlineFile(code.replace(HINT_COMMENT_PATTERN, "<$1/>"))

        RsInlayParameterHints.smartOption.set(smart)

        myFixture.testInlays({ (it.renderer as HintRenderer).text }) { it.renderer is HintRenderer }
    }

    companion object {
        private val HINT_COMMENT_PATTERN = Regex("""/\*(hint.*?)\*/""")
    }
}
