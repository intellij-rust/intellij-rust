/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.type

import org.intellij.lang.annotations.Language
import org.rust.ide.presentation.insertionSafeTextWithLifetimes
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
                                  //^ S
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

    fun `test array`() = testType("""
        type T = [i32; 2];
               //^ [i32; 2]
    """)

    fun `test array with expr`() = testType("""
        type T = [i32; 2 + 2];
               //^ [i32; 4]
    """)

    fun `test array with const`() = testType("""
        const COUNT: usize = 2;
        type T = [i32; COUNT];
               //^ [i32; 2]
    """)

    fun `test array with complex size`() = testType("""
        const COUNT: usize = 2;
        type T = [i32; (2 * COUNT + 3) << (4 / 2)];
               //^ [i32; 28]
    """)

    fun `test array with negative size`() = testType("""
        type T = [i32; 2 - 3];
               //^ [i32; <unknown>]
    """)

    fun `test array with not usize size expr`() = testType("""
        const COUNT: i32 = 2;
        type T = [i32; COUNT];
               //^ [i32; <unknown>]
    """)

    fun `test array with recursive expr`() = testType("""
        const COUNT: usize = 2 + COUNT;
        type T = [i32; COUNT];
               //^ [i32; <unknown>]
    """)

    fun `test associated type`() = testType("""
        trait Trait<T> {
            type Item;
        }
        fn foo<B: Trait<u8>>(_: B) {
            let a: B::Item;
        }           //^ <B as Trait<u8>>::Item
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
        }                         //^ S
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
        }                         //^ S
    """)

    fun `test generic trait object`() = testType("""
        trait Trait<A> {}
        fn foo(_: &Trait<u8>) { unimplemented!() }
                  //^ Trait<u8>
    """)

    fun `test generic 'dyn Trait' trait object`() = testType("""
        trait Trait<A> {}
        fn foo(_: &dyn Trait<u8>) { unimplemented!() }
                  //^ Trait<u8>
    """)

    fun `test trait object with bound associated type`() = testType("""
        trait Trait { type Item; }
        fn foo(_: &Trait<Item=u8>) { unimplemented!() }
                  //^ Trait<Item=u8>
    """)

    fun `test impl Trait`() = testType("""
        trait Trait {}
        fn foo() -> impl Trait { unimplemented!() }
                  //^ impl Trait
    """)

    fun `test generic impl Trait`() = testType("""
        trait Trait<T> {}
        fn foo() -> impl Trait<u8> { unimplemented!() }
                  //^ impl Trait<u8>
    """)

    fun `test 'impl Trait' with bound associated type`() = testType("""
        trait Trait { type Item; }
        fn foo() -> impl Trait<Item=u8> { unimplemented!() }
                  //^ impl Trait<Item=u8>
    """)

    fun `test impl Trait1+Trait2`() = testType("""
        trait Trait1 {}
        trait Trait2 {}

        fn foo() -> impl Trait1+Trait2 { unimplemented!() }
                  //^ impl Trait1+Trait2
    """)

    fun `test primitive str ref with lifetime`() = testType("""
        type T = &'static str;
                //^ &'static str
    """, renderLifetimes = true)

    fun `test str ref with lifetime`() = testType("""
        type T<'a> = &'a str;
                    //^ &'a str
    """, renderLifetimes = true)

    fun `test str mut ref with lifetime`() = testType("""
        type T<'a> = &'a mut str;
                    //^ &'a mut str
    """, renderLifetimes = true)

    fun `test struct with lifetime`() = testType("""
        struct Struct<'a> {
            field: &'a i32,
        }         //^ &'a i32
    """, renderLifetimes = true)

    fun `test function with lifetime`() = testType("""
        fn id<'a>(x: &'a str) -> &'a str { x }
                    //^ &'a str
    """, renderLifetimes = true)

    fun `test impl trait with lifetime`() = testType("""
        trait Trait<'a> {
            fn foo(x: &'a str);
        }
        struct Struct {}
        impl<'b> Trait<'b> for Struct {
            fn foo(a: &'b str) {
                     //^ &'b str
            }
        }
    """, renderLifetimes = true)

    fun `test deep generic struct with lifetime`() = testType("""
        struct Struct<'a, T>(&'a Struct<'a, Struct<'a, &'a str>>);
                            //^ &'a Struct<'a, Struct<'a, &'a str>>
    """, renderLifetimes = true)

    fun `test deep generic struct with static lifetime`() = testType("""
        struct Struct<'a, T>(&'static Struct<'static, Struct<'static, &'a str>>);
                            //^ &'static Struct<'static, Struct<'static, &'a str>>
    """, renderLifetimes = true)

    fun `test deep generic struct with undeclared lifetime`() = testType("""
        struct Struct<'a, T>(&'b Struct<'b, Struct<'b, &'a str>>);
                            //^ &Struct<'_, Struct<'_, &'a str>>
    """, renderLifetimes = true)

    /**
     * Checks the type of the element in [code] pointed to by `//^` marker.
     */
    private fun testType(@Language("Rust") code: String, renderLifetimes: Boolean = false) {
        InlineFile(code)
        val (typeAtCaret, expectedType) = findElementAndDataInEditor<RsTypeReference>()

        val ty = typeAtCaret.type
        val renderedTy = if (renderLifetimes) ty.insertionSafeTextWithLifetimes else ty.toString()
        check(renderedTy == expectedType) {
            "$renderedTy != $expectedType"
        }
    }
}

