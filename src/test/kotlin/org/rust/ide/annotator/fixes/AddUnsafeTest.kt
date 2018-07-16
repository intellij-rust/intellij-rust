/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.annotator.fixes

import org.rust.ide.annotator.RsAnnotationTestBase

class AddUnsafeTest : RsAnnotationTestBase() {
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

    fun `test wrap function with a unsafe block`() = checkFixByText("Surround with unsafe block", """
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

    fun `test wrap function with a unsafe block 2`() = checkFixByText("Surround with unsafe block", """
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

    fun `test wrap function with a unsafe block inline`() = checkFixByText("Surround with unsafe block", """
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
}
