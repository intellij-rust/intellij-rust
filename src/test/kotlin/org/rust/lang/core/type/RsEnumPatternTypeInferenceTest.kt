/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.type

class RsEnumPatternTypeInferenceTest : RsTypificationTestBase() {
    fun `test enum pattern`() = testExpr("""
        enum E {
            X
        }
        fn main() {
            let x = E::X;
            x;
          //^ E
        }
    """)

    fun `test enum pattern with unnamed args`() = testExpr("""
        enum E {
            X(i32, i16)
        }
        fn bar() -> E {}

        fn main() {
            let E::X(_, i) = bar();
            i;
          //^ i16
        }
    """)

    fun `test enum pattern with named args`() = testExpr("""
        enum E {
            X { _1: i32, _2: i64 }
        }
        fn bar() -> E {}

        fn main() {
            let E::X(_, i) = bar();
            i;
          //^ <unknown>
        }
    """)

    fun `test enum tuple out of bounds`() = testExpr("""
        enum E {
            V(i32, i32)
        }

        fn main() {
            let E::V(_, _, x): E = unimplemented!();
            x
          //^ <unknown>
        }
    """)

    fun `test struct tuple`() = testExpr("""
        struct Centimeters(f64);
        struct Inches(i32);

        impl Inches {
            fn to_centimeters(&self) -> Centimeters {
                let &Inches(inches) = self;

                inches;
              //^ i32
            }
        }
    """)

    fun `test binding with pat`() = testExpr("""
        struct S { x: i32, y: i32 }

        enum Result {
            Ok(S),
            Failure
        }

        fn foo(r: Result) {
            let Result::Ok(s @ S { x, .. }) = r;
            s
          //^ S
        }
    """)

    fun `test binding with pat failure 1`() = testExpr("""
        struct S { x: i32, y: i32 }

        enum Result {
            Ok(S),
            Failure
        }

        fn foo(r: Result) {
            let Result::Ok(s @ S { j /* non-existing field */ }) = r;
            s
          //^ S
        }
    """)

    fun `test binding with pat failure 2`() = testExpr("""
        struct S { x: i32, y: i32 }

        enum Result {
            Ok(S),
            Failure
        }

        fn foo(r: Result) {
            let Result::Ok(s @ S { x /* missing fields */ }) = r;
            s
          //^ S
        }
    """)
}

