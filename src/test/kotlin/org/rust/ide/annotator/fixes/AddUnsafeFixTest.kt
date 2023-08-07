/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.annotator.fixes

import org.rust.ide.annotator.RsAnnotationTestBase
import org.rust.ide.annotator.RsAnnotationTestFixture
import org.rust.ide.annotator.RsErrorAnnotator
import org.rust.ide.annotator.RsUnsafeExpressionAnnotator

class AddUnsafeFixTest : RsAnnotationTestBase() {

    override fun createAnnotationFixture(): RsAnnotationTestFixture<Unit> {
        val annotatorClasses = listOf(RsUnsafeExpressionAnnotator::class, RsErrorAnnotator::class)
        return RsAnnotationTestFixture(this, myFixture, annotatorClasses = annotatorClasses)
    }

    fun `test add unsafe to function`() = checkFixByText("Add unsafe to function", """
        unsafe fn foo() {}

        fn bar() {
            <error>foo()/*caret*/</error>;
        }
    """, """
        unsafe fn foo() {}

        unsafe fn bar() {
            foo();
        }
    """)

    fun `test add unsafe to function (method)`() = checkFixByText("Add unsafe to function", """
        struct S;

        impl S {
            unsafe fn foo(&self) {}
        }

        fn bar() {
            let s = S;
            <error>s.foo()/*caret*/</error>;
        }
    """, """
        struct S;

        impl S {
            unsafe fn foo(&self) {}
        }

        unsafe fn bar() {
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

        fn bar() {
            for i in 0..1 {
                <error>foo()/*caret*/</error>;
            }
        }
    """, """
        unsafe fn foo() {}

        unsafe fn bar() {
            for i in 0..1 {
                foo();
            }
        }
    """)

    fun `test add unsafe inside loop`() = checkFixByText("Add unsafe to function", """
        unsafe fn foo() {}

        fn bar() {
            loop {
                <error>foo()/*caret*/</error>;
            }
        }
    """, """
        unsafe fn foo() {}

        unsafe fn bar() {
            loop {
                foo();
            }
        }
    """)

    fun `test add unsafe inside if`() = checkFixByText("Add unsafe to function", """
        unsafe fn foo() {}

        fn bar() {
            if true {
                <error>foo()/*caret*/</error>;
            }
        }
    """, """
        unsafe fn foo() {}

        unsafe fn bar() {
            if true {
                foo();
            }
        }
    """)

    fun `test do not provide 'add unsafe to function' in the main function 1`() = checkNoAddUnsafeFixOnMainFun("main.rs")
    fun `test do not provide 'add unsafe to function' in the main function 2`() = checkNoAddUnsafeFixOnMainFun("build.rs")
    fun `test do not provide 'add unsafe to function' in the main function 3`() = checkNoAddUnsafeFixOnMainFun("example/a.rs")

    private fun checkNoAddUnsafeFixOnMainFun(filePath: String) = checkFixIsUnavailableByFileTree("Add unsafe to function", """
    //- $filePath
        unsafe fn foo() {}

        fn main() {
            <error descr="Call to unsafe function requires unsafe function or block [E0133]">foo()/*caret*/</error>;
        }
    """)

    fun `test add unsafe to function (similar main function)`() = checkFixByText("Add unsafe to function", """
        unsafe fn foo() {}

        fn main() {
            fn main() {
                <error>foo()/*caret*/</error>;
            }
        }
    """, """
        unsafe fn foo() {}

        fn main() {
            unsafe fn main() {
                foo();
            }
        }
    """)

    fun `test add unsafe to main function with no_main attr`() = checkFixByText("Add unsafe to function", """
        #![no_main]

        unsafe fn foo() {}

        fn main() {
            <error>foo()/*caret*/</error>;
        }
    """, """
        #![no_main]

        unsafe fn foo() {}

        unsafe fn main() {
            foo()/*caret*/;
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

    fun `test wrap tail expression with an unsafe block 1`() = checkFixByText("Surround with unsafe block", """
        static mut ERROR: i32 = 0;
        fn main() {
            if <error>ERROR/*caret*/</error> != 0 {
                panic!();
            }
        }
    """, """
        static mut ERROR: i32 = 0;
        fn main() {
            unsafe {
                if ERROR != 0 {
                    panic!();
                }
            }
        }
    """)

    fun `test wrap tail expression with an unsafe block 2`() = checkFixByText("Surround with unsafe block", """
        static mut ERROR: i32 = 0;
        fn main() {
            if true {
                if <error>ERROR/*caret*/</error> != 0 {
                    panic!();
                }
            }
        }
    """, """
        static mut ERROR: i32 = 0;
        fn main() {
            if true {
                unsafe {
                    if ERROR != 0 {
                        panic!();
                    }
                }
            }
        }
    """)

    fun `test wrap tail expression inside closure with an unsafe block`() = checkFixByText("Surround with unsafe block", """
        static mut ERROR: i32 = 0;
        fn main() {
            bar(|| {
                if <error>ERROR/*caret*/</error> != 0 {
                    panic!();
                }
            });
        }
        fn bar(f: fn()) {}
    """, """
        static mut ERROR: i32 = 0;
        fn main() {
            bar(|| {
                unsafe {
                    if ERROR != 0 {
                        panic!();
                    }
                }
            });
        }
        fn bar(f: fn()) {}
    """)

    fun `test wrap item inside block with an unsafe block`() = checkFixByText("Surround with unsafe block", """
        static mut C1: i32 = 0;
        fn main() {
            static C2: i32 = <error>C1/*caret*/</error>;
        }
    """, """
        static mut C1: i32 = 0;
        fn main() {
            static C2: i32 = unsafe { C1 };
        }
    """)

    fun `test add unsafe to impl`() = checkFixByText("Add unsafe to impl", """
        unsafe trait Trait {}

        impl <error>Trait/*caret*/</error> for () {}
    """, """
        unsafe trait Trait {}

        unsafe impl Trait/*caret*/ for () {}
    """)

    fun `test no add unsafe to function fix if test`() = checkFixIsUnavailable("Add unsafe to function", """
        unsafe fn foo() {}

        #[test]
        fn some_test() {
            <error>foo()<caret></error>;
        }
    """)

    fun `test no add unsafe to function fix if doctest`() = checkFixIsUnavailable("Add unsafe to function", """
        /// ```
        /// unsafe fn foo() {}
        /// foo()<caret>;
        /// ```
        fn bar() {}
    """)

    fun `test no add unsafe to function fix if implementing safe function`() = checkFixIsUnavailable("Add unsafe to function", """
        unsafe fn unsafe_foo() {}

        trait Foo {
            fn foo();
        }

        struct Bar;

        impl Foo for Bar {
            fn foo() {
                <error>unsafe_foo()<caret></error>;
            }
        }
    """)
}
