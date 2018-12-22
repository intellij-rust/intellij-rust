/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.annotator

class RsUnsafeExpressionAnnotatorTest : RsAnnotationTestBase() {
    fun `test extern static requires unsafe`() = checkErrors("""
        extern {
            static C: i32;
        }

        fn main() {
            let a = <error descr="Use of extern static is unsafe and requires unsafe function or block [E0133]">C</error>;
        }
    """)

    fun `test extern static in unsafe block`() = checkWarnings("""
        extern {
            static C: i32;
        }

        fn main() {
            unsafe { let a = <weak_warning descr="Use of unsafe extern static">C</weak_warning>; }
        }
    """)

    fun `test need unsafe static mutable`() = checkErrors("""
        static mut test : u8 = 0;

        fn main() {
            <error descr="Use of mutable static is unsafe and requires unsafe function or block [E0133]">test</error> += 1;
        }
    """)

    fun `test mutable static in unsafe block`() = checkWarnings("""
        static mut test : u8 = 0;

        fn main() {
            unsafe { <weak_warning descr="Use of unsafe mutable static">test</weak_warning> += 1; }
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

    fun `test unsafe call unsafe`() = checkWarnings("""
        unsafe fn foo() {}
        unsafe fn bar() { <weak_warning descr="Call to unsafe function">foo</weak_warning>(); }
    """)

    fun `test pointer dereference`() = checkErrors("""
        fn main() {
            let char_ptr: *const char = 42 as *const _;
            let val = <error descr="Dereference of raw pointer requires unsafe function or block [E0133]">*char_ptr</error>;
        }
    """)

    fun `test pointer dereference in unsafe block`() = checkWarnings("""
        fn main() {
            let char_ptr: *const char = 42 as *const _;
            let val = unsafe { <weak_warning descr="Unsafe dereference of raw pointer">*char_ptr</weak_warning> };
        }
    """)

    fun `test pointer dereference in unsafe fn`() = checkWarnings("""
        fn main() {
        }
        unsafe fn foo() {
            let char_ptr: *const char = 42 as *const _;
            let val = <weak_warning descr="Unsafe dereference of raw pointer">*char_ptr</weak_warning>;
        }
    """)
}
