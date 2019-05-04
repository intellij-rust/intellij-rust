/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.annotator.fixes

import org.rust.ide.annotator.RsAnnotatorTestBase
import org.rust.ide.annotator.RsErrorAnnotator

class ReplaceWithStdMemDropFixTest : RsAnnotatorTestBase(RsErrorAnnotator::class.java) {

    fun `test correct self type call expr`() = checkFixByText("Replace with `std::mem::drop`", """
        struct X;
        #[lang = "drop"]
        pub trait Drop {
            fn drop(&mut self);
        }
        impl Drop for X {
            fn drop(&mut self) {}
        }

        fn main() {
            let mut x = X {};
            <error descr="Explicit calls to `drop` are forbidden. Use `std::mem::drop` instead [E0040]">X::drop/*caret*/</error>(&mut x);
        }
    """, """
        struct X;
        #[lang = "drop"]
        pub trait Drop {
            fn drop(&mut self);
        }
        impl Drop for X {
            fn drop(&mut self) {}
        }

        fn main() {
            let mut x = X {};
            std::mem::drop(x);
        }
    """)

    fun `test incorrect self type call expr`() = checkFixByText("Replace with `std::mem::drop`", """
        struct X;
        #[lang = "drop"]
        pub trait Drop {
            fn drop(&mut self);
        }
        impl Drop for X {
            fn drop(&mut self) {}
        }

        fn main() {
            let mut x = X {};
            <error descr="Explicit calls to `drop` are forbidden. Use `std::mem::drop` instead [E0040]">X::drop/*caret*/</error>(&mut x, 123, "foo");
        }
    """, """
        struct X;
        #[lang = "drop"]
        pub trait Drop {
            fn drop(&mut self);
        }
        impl Drop for X {
            fn drop(&mut self) {}
        }

        fn main() {
            let mut x = X {};
            std::mem::drop(&mut x, 123, "foo");
        }
    """)

    fun `test correct Drop type call expr`() = checkFixByText("Replace with `std::mem::drop`", """
        struct X;
        #[lang = "drop"]
        pub trait Drop {
            fn drop(&mut self);
        }
        impl Drop for X {
            fn drop(&mut self) {}
        }

        fn main() {
            let mut x = X {};
            <error descr="Explicit calls to `drop` are forbidden. Use `std::mem::drop` instead [E0040]">Drop::drop/*caret*/</error>(&mut x);
        }
    """, """
        struct X;
        #[lang = "drop"]
        pub trait Drop {
            fn drop(&mut self);
        }
        impl Drop for X {
            fn drop(&mut self) {}
        }

        fn main() {
            let mut x = X {};
            std::mem::drop(x);
        }
    """)

    fun `test incorrect Drop type call expr`() = checkFixByText("Replace with `std::mem::drop`", """
        struct X;
        #[lang = "drop"]
        pub trait Drop {
            fn drop(&mut self);
        }
        impl Drop for X {
            fn drop(&mut self) {}
        }

        fn main() {
            let mut x = X {};
            <error descr="Explicit calls to `drop` are forbidden. Use `std::mem::drop` instead [E0040]">Drop::drop/*caret*/</error>(&mut x, 123, "foo");
        }
    """, """
        struct X;
        #[lang = "drop"]
        pub trait Drop {
            fn drop(&mut self);
        }
        impl Drop for X {
            fn drop(&mut self) {}
        }

        fn main() {
            let mut x = X {};
            std::mem::drop(&mut x, 123, "foo");
        }
    """)

    fun `test correct method call`() = checkFixByText("Replace with `std::mem::drop`", """
        struct X;
        #[lang = "drop"]
        pub trait Drop {
            fn drop(&mut self);
        }
        impl Drop for X {
            fn drop(&mut self) {}
        }

        fn main() {
            let mut x = X {};
            x.<error descr="Explicit calls to `drop` are forbidden. Use `std::mem::drop` instead [E0040]">drop/*caret*/</error>();
        }
    """, """
        struct X;
        #[lang = "drop"]
        pub trait Drop {
            fn drop(&mut self);
        }
        impl Drop for X {
            fn drop(&mut self) {}
        }

        fn main() {
            let mut x = X {};
            std::mem::drop(x);
        }
    """)


    fun `test incorrect method call`() = checkFixByText("Replace with `std::mem::drop`", """
        struct X;
        #[lang = "drop"]
        pub trait Drop {
            fn drop(&mut self);
        }
        impl Drop for X {
            fn drop(&mut self) {}
        }

        fn main() {
            let mut x = X {};
            x.<error descr="Explicit calls to `drop` are forbidden. Use `std::mem::drop` instead [E0040]">drop/*caret*/</error>(123, "foo");
        }
    """, """
        struct X;
        #[lang = "drop"]
        pub trait Drop {
            fn drop(&mut self);
        }
        impl Drop for X {
            fn drop(&mut self) {}
        }

        fn main() {
            let mut x = X {};
            std::mem::drop(x, 123, "foo");
        }
    """)

    fun `test correct chain method call`() = checkFixByText("Replace with `std::mem::drop`", """
        struct X;
        #[lang = "drop"]
        pub trait Drop {
            fn drop(&mut self);
        }
        impl Drop for X {
            fn drop(&mut self) {}
        }
        struct XFactory;
        impl XFactory {
            fn create_x(&self, foo: u32) -> X {
                X {}
            }
        }

        fn main() {
            XFactory {}.create_x(123).<error descr="Explicit calls to `drop` are forbidden. Use `std::mem::drop` instead [E0040]">drop/*caret*/</error>();
        }
    """, """
        struct X;
        #[lang = "drop"]
        pub trait Drop {
            fn drop(&mut self);
        }
        impl Drop for X {
            fn drop(&mut self) {}
        }
        struct XFactory;
        impl XFactory {
            fn create_x(&self, foo: u32) -> X {
                X {}
            }
        }

        fn main() {
            std::mem::drop(XFactory {}.create_x(123));
        }
    """)

    fun `test incorrect chain method call`() = checkFixByText("Replace with `std::mem::drop`", """
        struct X;
        #[lang = "drop"]
        pub trait Drop {
            fn drop(&mut self);
        }
        impl Drop for X {
            fn drop(&mut self) {}
        }
        struct XFactory;
        impl XFactory {
            fn create_x(&self, foo: u32) -> X {
                X {}
            }
        }

        fn main() {
            XFactory {}.create_x(123).<error descr="Explicit calls to `drop` are forbidden. Use `std::mem::drop` instead [E0040]">drop/*caret*/</error>(123, "foo");
        }
    """, """
        struct X;
        #[lang = "drop"]
        pub trait Drop {
            fn drop(&mut self);
        }
        impl Drop for X {
            fn drop(&mut self) {}
        }
        struct XFactory;
        impl XFactory {
            fn create_x(&self, foo: u32) -> X {
                X {}
            }
        }

        fn main() {
            std::mem::drop(XFactory {}.create_x(123), 123, "foo");
        }
    """)
}
