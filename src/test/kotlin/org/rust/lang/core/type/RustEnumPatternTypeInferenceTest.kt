package org.rust.lang.core.type

class RustEnumPatternTypeInferenceTest : RustTypificationTestBase() {
    fun testEnumPattern() = testExpr("""
        enum E {
            X
        }
        fn main() {
            let x = E::X;
            x;
          //^ E
        }
    """)

    fun testEnumPatternWithUnnamedArgs() = testExpr("""
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

    fun testEnumPatternWithNamedArgs() = testExpr("""
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

    fun testEnumTupleOutOfBounds() = testExpr("""
        enum E {
            V(i32, i32)
        }

        fn main() {
            let E::V(_, _, x): E = unimplemented!();
            x
          //^ <unknown>
        }
    """)

    fun testStructTuple() = testExpr("""
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

    fun testBindingWithPat() = testExpr("""
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

    fun testBindingWithPatFailure1() = testExpr("""
        struct S { x: i32, y: i32 }

        enum Result {
            Ok(S),
            Failure
        }

        fn foo(r: Result) {
            let Result::Ok(s @ S { j /* non-existing field */ }) = r;
            s
          //^ <unknown>
        }
    """)

    fun testBindingWithPatFailure2() = testExpr("""
        struct S { x: i32, y: i32 }

        enum Result {
            Ok(S),
            Failure
        }

        fn foo(r: Result) {
            let Result::Ok(s @ S { x /* missing fields */ }) = r;
            s
          //^ <unknown>
        }
    """)
}

