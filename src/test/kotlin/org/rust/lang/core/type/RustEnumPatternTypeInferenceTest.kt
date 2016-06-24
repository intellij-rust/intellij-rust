package org.rust.lang.core.type

class RustEnumPatternTypeInferenceTest: RustTypificationTestBase() {
    //language=RUST
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

    //language=RUST
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

    //language=RUST
    fun testEnumPatternWithNamedArgs() = testExpr("""
        enum E {
            X { _1: i32, _2: i64 }
        }
        fn bar() -> E {}

        fn main() {
            let E::X(_, i) = bar();
            i;
          //^ i64
        }
    """)

    //language=RUST
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
}

