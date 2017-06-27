/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.annotator.fixes

import org.rust.ide.annotator.RsAnnotatorTestBase

class AddUnsafeTest : RsAnnotatorTestBase() {
    fun `test add unsafe to function`() = checkQuickFix("Add unsafe to function", """
        unsafe fn foo() {}

        fn main() {
            foo()/*caret*/;
        }
    """, """
        unsafe fn foo() {}

        unsafe fn main() {
            foo();
        }
    """)

    fun `test add unsafe to function (method)`() = checkQuickFix("Add unsafe to function", """
        struct S;

        impl S {
            unsafe fn foo(&self) {}
        }

        fn main() {
            let s = S;
            s.foo()/*caret*/;
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

    fun `test add unsafe to block`() = checkQuickFix("Add unsafe to block", """
        unsafe fn foo() {}

        fn main() {
            {
                foo()/*caret*/;
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

    fun `test wrap function with a unsafe block`() = checkQuickFix("Surround with unsafe block", """
        unsafe fn foo() {}

        fn main() {
            foo()/*caret*/;
        }
    """, """
        unsafe fn foo() {}

        fn main() {
            unsafe { foo(); }
        }
    """)

    fun `test wrap function with a unsafe block 2`() = checkQuickFix("Surround with unsafe block", """
        unsafe fn foo() {}

        fn main() {
            foo()/*caret*/
        }
    """, """
        unsafe fn foo() {}

        fn main() {
            unsafe { foo() }
        }
    """)

    fun `test wrap function with a unsafe block inline`() = checkQuickFix("Surround with unsafe block", """
        unsafe fn pi() -> f64 { 3.14 }

        fn main() {
            let s = pi()/*caret*/ * 10.0;
        }
    """, """
        unsafe fn pi() -> f64 { 3.14 }

        fn main() {
            let s = unsafe { pi() } * 10.0;
        }
    """)

    fun `test wrap ptr deref with a unsafe block inline`() = checkQuickFix("Surround with unsafe block", """
        fn main() {
            let char_ptr: *const char = 42 as *const _;
            let val = *char_ptr/*caret*/;
        }
    """, """
        fn main() {
            let char_ptr: *const char = 42 as *const _;
            let val = unsafe { *char_ptr };
        }
    """)
}
