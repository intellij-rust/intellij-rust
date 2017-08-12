/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.type

class RsNumericLiteralTypeInferenceTest : RsTypificationTestBase() {
    fun `test infer rvalue from lvalue integer`() = testExpr("""
        fn main() {
            let a: u8 = 1;
        }             //^ u8
    """)

    fun `test infer rvalue from lvalue float`() = testExpr("""
        fn main() {
            let a: f32 = 1.0;
        }              //^ f32
    """)

    fun `test infer block rvalue from lvalue`() = testExpr("""
        fn main() {
            let a: u8 = { 1 };
        }               //^ u8
    """)

    fun `test infer if else rvalue from lvalue`() = testExpr("""
        fn main() {
            let a: u8 = if true { 1 } else { 1 };
        }                       //^ u8
    """)

    fun `test infer if else rvalue from lvalue 2`() = testExpr("""
        fn main() {
            let a: u8 = if true { 1 } else { 1 };
        }                                  //^ u8
    """)

    fun `test infer if else rvalue from lvalue 3`() = testExpr("""
        fn main() {
            let a: u8 = if true { 1 } else if true { 1 } else { 1 };
        }                                          //^ u8
    """)

    fun `test infer match rvalue from lvalue 3`() = testExpr("""
        fn main() {
            let a: u8 = match true {
                true => 1,
                false => 1,
            };         //^ u8
        }
    """)

    fun `test infer rvalue from lvalue assign`() = testExpr("""
        fn main() {
            let mut a = 1u8;
            a = 1;
        }     //^ u8
    """)

    fun `test infer rvalue from lvalue deref`() = testExpr("""
        fn main() {
            let mut a = 1u8;
            let b = &mut a;
            *b = 2;
        }      //^ u8
    """)

    fun `test infer ref rvalue from lvalue`() = testExpr("""
        fn main() {
            let a: &u8 = &1;
        }               //^ u8
    """)

    fun `test infer ref mut rvalue from lvalue`() = testExpr("""
        fn main() {
            let a: &u8 = &mut 1;
        }                   //^ u8
    """)

    fun `test infer unary minus rvalue from lvalue`() = testExpr("""
        fn main() {
            let a: i8 = -1;
        }              //^ i8
    """)

    fun `test infer not rvalue from lvalue`() = testExpr("""
        fn main() {
            let a: u8 = !1;
        }              //^ u8
    """)

    fun `test infer rvalue from lvalue tuple`() = testExpr("""
        fn main() {
            let a: (u8, u16) = (1, 2);
        }                     //^ u8
    """)

    fun `test infer array rvalue from array lvalue`() = testExpr("""
        fn main() {
            let a: [u8; 3] = [1, 2, 3];
        }                   //^ u8
    """)

    fun `test infer array rvalue from slice lvalue`() = testExpr("""
        fn main() {
            let a: &[u8] = &[1, 2, 3];
        }                  //^ u8
    """)

    fun `test infer repeat array rvalue from array lvalue`() = testExpr("""
        fn main() {
            let a: [u8; 3] = [1; 3];
        }                   //^ u8
    """)

    fun `test infer rvalue struct field`() = testExpr("""
        struct S { f: u8 }
        fn main() {
            let a = S { f: 1 };
        }                //^ u8
    """)

    fun `test infer rvalue from lvalue generic struct`() = testExpr("""
        struct S<T> { f: T }
        fn main() {
            let a: S<u8> = S { f: 1 };
        }                       //^ u8
    """)

    fun `test infer rvalue enum field`() = testExpr("""
        enum E { A { f: u8 } }
        fn main() {
            let a = E::A { f: 1 };
        }                   //^ u8
    """)

    fun `test infer rvalue from lvalue generic enum`() = testExpr("""
        enum E<T> { A { f: T } }
        fn main() {
            let a: E<u8> = E::A { f: 1 };
        }                          //^ u8
    """)

    fun `test infer rvalue from function param`() = testExpr("""
        fn foo(a: u8) {}
        fn main() {
            foo(1)
        }     //^ u8
    """)

    fun `test infer rvalue from generic function param`() = testExpr("""
        fn foo<T>(a: T) {}
        fn main() {
            foo::<u8>(1)
        }           //^ u8
    """)

    fun `test infer rvalue from inferred method param`() = testExpr("""
        struct S<T1>(T1);

        trait Foo<T2> { fn foo(&self, t: T2) {} }
        impl<T3> Foo<T3> for S<T3> {}

        fn main() {
            S(1u8).foo(1);
        }            //^ u8
    """)
}
