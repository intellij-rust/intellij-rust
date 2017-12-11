/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.type

class RsNumericLiteralTypeInferenceTest : RsTypificationTestBase() {
    fun `test default float`() = testExpr("""
        fn main() {
            let a = 1.0;
        }           //^ f64
    """)

    fun `test f32`() = testExpr("""
        fn main() {
            let a = 1.0f32;
        }           //^ f32
    """)

    fun `test f64`() = testExpr("""
        fn main() {
            let a = 1.0f64;
        }           //^ f64
    """)

    fun `test default integer`() = testExpr("""
        fn main() {
            let a = 42;
        }          //^ i32
    """)

    fun `test i8`() = testExpr("""
        fn main() {
            let a = 42i8;
        }          //^ i8
    """)

    fun `test i16`() = testExpr("""
        fn main() {
            let a = 42i16;
        }          //^ i16
    """)

    fun `test i32`() = testExpr("""
        fn main() {
            let a = 42i32;
        }          //^ i32
    """)

    fun `test i64`() = testExpr("""
        fn main() {
            let a = 42i64;
        }          //^ i64
    """)

    fun `test i128`() = testExpr("""
        fn main() {
            let a = 42i128;
        }          //^ i128
    """)

    fun `test isize`() = testExpr("""
        fn main() {
            let a = 42isize;
        }          //^ isize
    """)

    fun `test u8`() = testExpr("""
        fn main() {
            let a = 42u8;
        }          //^ u8
    """)

    fun `test u16`() = testExpr("""
        fn main() {
            let a = 42u16;
        }          //^ u16
    """)

    fun `test u32`() = testExpr("""
        fn main() {
            let a = 42u32;
        }          //^ u32
    """)

    fun `test u64`() = testExpr("""
        fn main() {
            let a = 42u64;
        }          //^ u64
    """)

    fun `test u128`() = testExpr("""
        fn main() {
            let a = 42u128;
        }          //^ u128
    """)

    fun `test usize`() = testExpr("""
        fn main() {
            let a = 42usize;
        }         //^ usize
    """)

    fun `test usize with outer attribute`() = testExpr("""
        fn main() {
            let a = #[foo] 42usize;
        }                //^ usize
    """)

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

    fun `test infer coerce reference to ptr`() = testExpr("""
        fn main() {
            let a: *const u8 = &0;
        }                     //^ u8
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

    fun `test integer unification`() = testExpr("""
        fn main() {
            let a = 0;
                  //^ u8
            let b: u8 = a;
        }
    """)

    fun `test float unification`() = testExpr("""
        fn main() {
            let mut a = 0.0;
                      //^ f32
            let b: f32 = a;
        }
    """)

    fun `test integer unification assign`() = testExpr("""
        fn main() {
            let mut a = 0;
                      //^ u8
            a = 1u8;
        }
    """)

    fun `test integer unification struct field`() = testExpr("""
        struct S { f: u8 }
        fn main() {
            let a = 0;
                  //^ u8
            S { f: a };
        }
    """)

    fun `test integer unification method param`() = testExpr("""
        fn foo(a: u8) {}
        fn main() {
            let a = 0;
                  //^ u8
            foo(a);
        }
    """)

    fun `test integer unification generic method param`() = testExpr("""
        fn foo<T>(a: T, b: T) {}
        fn main() {
            let a = 0;
                  //^ u8
            let b = 0u8;
            foo(a, b);
        }
    """)

    fun `test integer unification repeat expr`() = testExpr("""
        fn main() {
            let a = 0;
                  //^ u8
            let b: [u8; 1] = [a; 1];
        }
    """)

    fun `test integer unification array expr`() = testExpr("""
        fn main() {
            let a = 0;
                  //^ u8
            let b: [u8; 3] = [1, a, 3];
        }
    """)

    fun `test integer unification tuple expr`() = testExpr("""
        fn main() {
            let a = 0;
                  //^ u8
            let b: (u8, u8) = (a, 1);
        }
    """)

    fun `test integer unification block`() = testExpr("""
        fn main() {
        let a = 0;
              //^ u8
        let b: u8 = { a };
        }
    """)

    fun `test integer unification if else`() = testExpr("""
        fn main() {
        let a = 0;
              //^ u8
            let b: u8 = if true { a } else { 1 };
        }
    """)

    fun `test integer unification if else 2`() = testExpr("""
        fn main() {
        let a = 0;
              //^ u8
            let b: u8 = if true { 1 } else { a };
        }
    """)

    fun `test integer unification if else 3`() = testExpr("""
        fn main() {
        let a = 0;
              //^ u8
            let b: u8 = if true { 1 } else if true { a } else { 1 };
        }
    """)

    fun `test integer unification match`() = testExpr("""
        fn main() {
        let a = 0;
              //^ u8
            let b: u8 = match true {
                true => a,
                false => 1,
            };
        }
    """)
}
