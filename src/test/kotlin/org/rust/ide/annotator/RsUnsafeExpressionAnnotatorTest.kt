/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.annotator

class RsUnsafeExpressionAnnotatorTest : RsAnnotatorTestBase(RsUnsafeExpressionAnnotator::class.java) {
    fun `test extern static requires unsafe`() = checkErrors("""
        extern {
            static C: i32;
        }

        fn main() {
            let a = <error descr="Use of extern static is unsafe and requires unsafe function or block [E0133]">C</error>;
        }
    """)

    fun `test extern static in unsafe block`() = checkHighlighting("""
        extern {
            static C: i32;
        }

        fn main() {
            unsafe { let a = <info descr="Use of unsafe extern static">C</info>; }
        }
    """)

    fun `test need unsafe static mutable`() = checkErrors("""
        static mut test : u8 = 0;

        fn main() {
            <error descr="Use of mutable static is unsafe and requires unsafe function or block [E0133]">test</error> += 1;
        }
    """)

    fun `test mutable static in unsafe block`() = checkHighlighting("""
        static mut test : u8 = 0;

        fn main() {
            unsafe { <info descr="Use of unsafe mutable static">test</info> += 1; }
        }
    """)

    fun `test need unsafe function`() = checkErrors("""
        struct S;

        impl S {
            unsafe fn foo(&self) { return; }
        }

        fn main() {
            let s = S;
            <error descr="Call to unsafe function requires unsafe function or block [E0133]">s.foo()</error>;
        }
    """)

    fun `test need unsafe block`() = checkErrors("""
        struct S;

        impl S {
            unsafe fn foo(&self) { return; }
        }

        fn main() {
            {
                let s = S;
                <error descr="Call to unsafe function requires unsafe function or block [E0133]">s.foo()</error>;
            }
        }
    """)

    fun `test need unsafe 2`() = checkErrors("""
        unsafe fn foo() { return; }

        fn main() {
            <error>foo()</error>;
        }
    """)

    fun `test external ABI is unsafe`() = checkErrors("""
        extern "C" { fn foo(); }

        fn main() {
            <error descr="Call to unsafe function requires unsafe function or block [E0133]">foo()</error>;
        }
    """)


    fun `test is unsafe block`() = checkErrors("""
        unsafe fn foo() {}

        fn main() {
            unsafe {
                {
                    foo();
                }
            }
        }
    """)

    fun `test is unsafe function`() = checkErrors("""
        unsafe fn foo() {}

        fn main() {
            unsafe {
                fn bar() {
                    <error>foo()</error>;
                }
            }
        }
    """)

    fun `test unsafe call unsafe`() = checkHighlighting("""
        unsafe fn foo() {}
        unsafe fn bar() { <info descr="Call to unsafe function">foo</info>(); }
    """)

    fun `test pointer dereference`() = checkErrors("""
        fn main() {
            let char_ptr: *const char = 42 as *const _;
            let val = <error descr="Dereference of raw pointer requires unsafe function or block [E0133]">*char_ptr</error>;
        }
    """)

    fun `test pointer dereference in unsafe block`() = checkHighlighting("""
        fn main() {
            let char_ptr: *const char = 42 as *const _;
            let val = unsafe { <info descr="Unsafe dereference of raw pointer">*char_ptr</info> };
        }
    """)

    fun `test pointer dereference in unsafe fn`() = checkHighlighting("""
        fn main() {
        }
        unsafe fn foo() {
            let char_ptr: *const char = 42 as *const _;
            let val = <info descr="Unsafe dereference of raw pointer">*char_ptr</info>;
        }
    """)
}
