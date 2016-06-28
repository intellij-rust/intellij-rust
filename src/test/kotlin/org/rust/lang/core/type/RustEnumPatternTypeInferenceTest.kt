package org.rust.lang.core.type

class RustEnumPatternTypeInferenceTest: RustTypificationTestBase() {
    //language=Rust
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

    //language=Rust
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

    //language=Rust
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

    //language=Rust
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

