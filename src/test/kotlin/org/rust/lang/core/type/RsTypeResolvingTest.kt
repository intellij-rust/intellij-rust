/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.type

import org.intellij.lang.annotations.Language
import org.rust.lang.core.psi.RsTypeReference
import org.rust.lang.core.types.type

class RsTypeResolvingTest : RsTypificationTestBase() {
    fun `test path`() = testType("""
        struct Spam;

        fn main() {
            let _: Spam = Spam;
                 //^ Spam
        }
    """)

    fun `test unit`() = testType("""
        fn main() {
            let _: () = ();
                 //^ ()
        }
    """)

    fun `test tuple`() = testType("""
        struct S;
        struct T;
        fn main() {
            let _: (S, T) = (S, T);
                 //^ (S, T)
        }
    """)

    // TODO `<S as T>::Assoc` should be unified to `S`
    fun `test qualified path`() = testType("""
        trait T {
            type Assoc;
        }

        struct S;

        impl T for S {
            type Assoc = S;
        }

        fn main() {
            let _: <S as T>::Assoc = S;
                 //^ <S as T>::Assoc
        }
    """)

    fun `test enum`() = testType("""
        enum E { X }

        fn main() {
            let _: E = E::X;
                 //^ E
        }
    """)

    fun `test type item`() = testType("""
        enum E { X }

        type A = E;

        fn main() {
            let _: E = A::X;
                 //^ E
        }
    """)

    fun `test Self type`() = testType("""
        struct S;
        trait T { fn new() -> Self; }

        impl T for S { fn new() -> Self { S } }
                                  //^ Self
    """)

    fun `test primitive bool`() = testType("""
        type T = bool;
                  //^ bool
    """)

    fun `test primitive char`() = testType("""
        type T = char;
                  //^ char
    """)

    fun `test primitive f32`() = testType("""
        type T = f32;
                 //^ f32
    """)

    fun `test primitive f64`() = testType("""
        type T = f64;
                 //^ f64
    """)

    fun `test primitive i8`() = testType("""
        type T = i8;
                //^ i8
    """)

    fun `test primitive i16`() = testType("""
        type T = i16;
                 //^ i16
    """)

    fun `test primitive i32`() = testType("""
        type T = i32;
                 //^ i32
    """)

    fun `test primitive i64`() = testType("""
        type T = i64;
                 //^ i64
    """)

    fun `test primitive isize`() = testType("""
        type T = isize;
                   //^ isize
    """)

    fun `test primitive u8`() = testType("""
        type T = u8;
                //^ u8
    """)

    fun `test primitive u16`() = testType("""
        type T = u16;
                 //^ u16
    """)

    fun `test primitive u32`() = testType("""
        type T = u32;
                 //^ u32
    """)

    fun `test primitive u64`() = testType("""
        type T = u64;
                 //^ u64
    """)

    fun `test primitive usize`() = testType("""
        type T = usize;
                   //^ usize
    """)

    fun `test primitive str`() = testType("""
        type T = str;
                 //^ str
    """)

    fun `test primitive str ref`() = testType("""
        type T = &'static str;
                 //^ &str
    """)

    fun `test fn pointer`() = testType("""
        type T = fn(i32) -> i32;
               //^ fn(i32) -> i32
    """)

    fun `test associated types for impl`() = testType("""
        trait A {
            type Item;
            fn foo(self) -> Self::Item;
        }
        struct S;
        impl A for S {
            type Item = S;
            fn foo(self) -> Self::Item { S }
        }                         //^ <Self as A>::Item
    """)

    fun `test inherited associated types for impl`() = testType("""
        trait A { type Item; }
        trait B: A {
            fn foo(self) -> Self::Item;
        }
        struct S;
        impl A for S { type Item = S; }
        impl B for S {
            fn foo(self) -> Self::Item { S }
        }                         //^ <Self as A>::Item
    """)

    fun `test generic trait object`() = testType("""
        trait Trait<A> {}

        fn foo(_: &Trait<u8>) { unimplemented!() }
                  //^ Trait<u8>
    """)

    fun `test impl Trait`() = testType("""
        trait Trait { }

        fn foo() -> impl Trait { unimplemented!() }
                  //^ impl Trait
    """)

    fun `test impl Trait1+Trait2`() = testType("""
        trait Trait1 { }
        trait Trait2 { }

        fn foo() -> impl Trait1+Trait2 { unimplemented!() }
                  //^ impl Trait1+Trait2
    """)

    /**
     * Checks the type of the element in [code] pointed to by `//^` marker.
     */
    private fun testType(@Language("Rust") code: String) {
        InlineFile(code)
        val (typeAtCaret, expectedType) = findElementAndDataInEditor<RsTypeReference>()

        check(typeAtCaret.type.toString() == expectedType) {
            "${typeAtCaret.type} != $expectedType"
        }
    }
}

