/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.type

import org.assertj.core.api.Assertions.assertThat
import org.intellij.lang.annotations.Language
import org.rust.lang.core.psi.RsTypeReference
import org.rust.lang.core.types.type

class RsTypeResolvingTest : RsTypificationTestBase() {
    fun testPath() = testType("""
        struct Spam;

        fn main() {
            let _: Spam = Spam;
                 //^ Spam
        }
    """)

    fun testUnit() = testType("""
        fn main() {
            let _: () = ();
                 //^ ()
        }
    """)

    fun testTuple() = testType("""
        struct S;
        struct T;
        fn main() {
            let _: (S, T) = (S, T);
                 //^ (S, T)
        }
    """)

    // TODO `<S as T>::Assoc` should be unified to `S`
    fun testQualifiedPath() = testType("""
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

    fun testEnum() = testType("""
        enum E { X }

        fn main() {
            let _: E = E::X;
                 //^ E
        }
    """)

    fun testTypeItem() = testType("""
        enum E { X }

        type A = E;

        fn main() {
            let _: E = A::X;
                 //^ E
        }
    """)

    fun testSelfType() = testType("""
        struct S;
        trait T { fn new() -> Self; }

        impl T for S { fn new() -> Self { S } }
                                  //^ Self
    """)

    fun testPrimitiveBool() = testType("""
        type T = bool;
                  //^ bool
    """)

    fun testPrimitiveChar() = testType("""
        type T = char;
                  //^ char
    """)

    fun testPrimitiveF32() = testType("""
        type T = f32;
                 //^ f32
    """)

    fun testPrimitiveF64() = testType("""
        type T = f64;
                 //^ f64
    """)

    fun testPrimitiveI8() = testType("""
        type T = i8;
                //^ i8
    """)

    fun testPrimitiveI16() = testType("""
        type T = i16;
                 //^ i16
    """)

    fun testPrimitiveI32() = testType("""
        type T = i32;
                 //^ i32
    """)

    fun testPrimitiveI64() = testType("""
        type T = i64;
                 //^ i64
    """)

    fun testPrimitiveISize() = testType("""
        type T = isize;
                   //^ isize
    """)

    fun testPrimitiveU8() = testType("""
        type T = u8;
                //^ u8
    """)

    fun testPrimitiveU16() = testType("""
        type T = u16;
                 //^ u16
    """)

    fun testPrimitiveU32() = testType("""
        type T = u32;
                 //^ u32
    """)

    fun testPrimitiveU64() = testType("""
        type T = u64;
                 //^ u64
    """)

    fun testPrimitiveUSize() = testType("""
        type T = usize;
                   //^ usize
    """)

    fun testPrimitiveStr() = testType("""
        type T = str;
                 //^ str
    """)

    fun testPrimitiveStrRef() = testType("""
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
        }                         //^ <Self as A>::Item
    """)

    /**
     * Checks the type of the element in [code] pointed to by `//^` marker.
     */
    private fun testType(@Language("Rust") code: String) {
        InlineFile(code)
        val (typeAtCaret, expectedType) = findElementAndDataInEditor<RsTypeReference>()

        assertThat(typeAtCaret.type.toString())
            .isEqualTo(expectedType)
    }
}

