/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.annotator.fixes

import org.rust.ide.annotator.RsAnnotatorTestBase
import org.rust.ide.annotator.RsUnsafeExpressionAnnotator

class AddUnsafeFixTest : RsAnnotatorTestBase(RsUnsafeExpressionAnnotator::class) {
    fun `test add unsafe to function`() = checkFixByText("Add unsafe to function", """
        unsafe fn foo() {}

        fn main() {
            <error>foo()/*caret*/</error>;
        }
    """, """
        unsafe fn foo() {}

        unsafe fn main() {
            foo();
        }
    """)

    fun `test add unsafe to function (method)`() = checkFixByText("Add unsafe to function", """
        struct S;

        impl S {
            unsafe fn foo(&self) {}
        }

        fn main() {
            let s = S;
            <error>s.foo()/*caret*/</error>;
        }
    """, """
        struct S;

        impl S {
            unsafe fn foo(&self) {}
        }

        unsafe fn main() {
            let s = S;
            s.foo();
        }
    """)

    fun `test add unsafe to function (external linkage)`() = checkFixByText("Add unsafe to function", """
        extern "C" { fn foo(); }

        fn test() {
            <error>foo()/*caret*/</error>;
        }
    """, """
        extern "C" { fn foo(); }

        unsafe fn test() {
            foo();
        }
    """)

    fun `test add unsafe to block`() = checkFixByText("Add unsafe to block", """
        unsafe fn foo() {}

        fn main() {
            {
                <error>foo()/*caret*/</error>;
            }
        }
    """, """
        unsafe fn foo() {}

        fn main() {
            unsafe {
                foo();
            }
        }
    """)

    fun `test add unsafe inside for`() = checkFixByText("Add unsafe to function", """
        unsafe fn foo() {}

        fn main() {
            for i in 0..1 {
                <error>foo()/*caret*/</error>;
            }
        }
    """, """
        unsafe fn foo() {}

        unsafe fn main() {
            for i in 0..1 {
                foo();
            }
        }
    """)

    fun `test add unsafe inside loop`() = checkFixByText("Add unsafe to function", """
        unsafe fn foo() {}

        fn main() {
            loop {
                <error>foo()/*caret*/</error>;
            }
        }
    """, """
        unsafe fn foo() {}

        unsafe fn main() {
            loop {
                foo();
            }
        }
    """)

    fun `test add unsafe inside if`() = checkFixByText("Add unsafe to function", """
        unsafe fn foo() {}

        fn main() {
            if true {
                <error>foo()/*caret*/</error>;
            }
        }
    """, """
        unsafe fn foo() {}

        unsafe fn main() {
            if true {
                foo();
            }
        }
    """)

    fun `test add unsafe inside if in a block`() = checkFixByText("Add unsafe to block", """
        unsafe fn foo() -> u32 { 0 }

        fn main() {
            let x = {
                if true {
                    <error>foo()/*caret*/</error>
                } else {
                    5
                }
            };
        }
    """, """
        unsafe fn foo() -> u32 { 0 }

        fn main() {
            let x = unsafe {
                if true {
                    foo()
                } else {
                    5
                }
            };
        }
    """)

    fun `test wrap function with an unsafe block`() = checkFixByText("Surround with unsafe block", """
        unsafe fn foo() {}

        fn main() {
            <error>foo()/*caret*/</error>;
        }
    """, """
        unsafe fn foo() {}

        fn main() {
            unsafe { foo(); }
        }
    """)

    fun `test wrap function with an unsafe block 2`() = checkFixByText("Surround with unsafe block", """
        unsafe fn foo() {}

        fn main() {
            <error>foo()/*caret*/</error>
        }
    """, """
        unsafe fn foo() {}

        fn main() {
            unsafe { foo() }
        }
    """)

    fun `test wrap function with an unsafe block inline`() = checkFixByText("Surround with unsafe block", """
        unsafe fn pi() -> f64 { 3.14 }

        fn main() {
            let s = <error>pi()/*caret*/</error> * 10.0;
        }
    """, """
        unsafe fn pi() -> f64 { 3.14 }

        fn main() {
            let s = unsafe { pi() } * 10.0;
        }
    """)

    fun `test wrap ptr deref with a unsafe block inline`() = checkFixByText("Surround with unsafe block", """
        fn main() {
            let char_ptr: *const char = 42 as *const _;
            let val = <error>*char_ptr/*caret*/</error>;
        }
    """, """
        fn main() {
            let char_ptr: *const char = 42 as *const _;
            let val = unsafe { *char_ptr };
        }
    """)

    fun `test wrap function call with unsafe block (external linkage)`() = checkFixByText("Surround with unsafe block", """
        extern "C" { fn foo(); }

        fn test() {
            <error>foo()/*caret*/</error>;
        }
    """, """
        extern "C" { fn foo(); }

        fn test() {
            unsafe { foo(); }
        }
    """)

    fun `test left-hand side assignment`() = checkFixByText("Surround with unsafe block", """
        fn test() {
            let buffer = 0xb00000 as *mut u8;
            <error>*buffer/*caret*/</error> = 5;
        }
    """, """
        fn test() {
            let buffer = 0xb00000 as *mut u8;
            unsafe { *buffer = 5; }
        }
    """)

    fun `test nested left-hand side assignment`() = checkFixByText("Surround with unsafe block", """
        extern "C" { fn foo() -> *mut u8; }

        fn test() {
            <error>*<error>foo()/*caret*/</error></error> = 5;
        }
    """, """
        extern "C" { fn foo() -> *mut u8; }

        fn test() {
            unsafe { *foo() = 5; }
        }
    """)
}
