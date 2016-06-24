package org.rust.lang.core.type

class RustExpressionTypeInferenceTest : RustTypificationTestBase() {
    //language=RUST
    fun testIfLetPattern() = testExpr("""
        fn main() {
            let _ = if let Some(x) = Some(92i32) { x } else { x };
                                                 //^ <unknown>
        }
    """)

    //language=RUST
    fun testLetTypeAscription() = testExpr("""
        struct S;
        struct T;

        fn main() {
            let (x, _): (S, T) = unimplemented!();
            x;
          //^ S
        }
    """)

    //language=RUST
    fun testLetInitExpr() = testExpr("""
        struct S;
        struct T;

        fn main() {
            let (_, x) = (S, T);
            x;
          //^ T
        }
    """)

    //language=RUST
    fun testNestedStructPattern() = testExpr("""
        struct S;
        struct T {
            s: S
        }

        fn main() {
            let T { s: x } = T { s: S };
            x;
          //^ S
        }
    """)

    //language=RUST
    fun testFnArgumentPattern() = testExpr("""
        struct S;
        struct T;

        fn main((x, _): (S, T)) {
            x;
          //^ S
        }
    """)

    //language=RUST
    fun testClosureArgument() = testExpr( """
        fn main() {
            let _ = |x: ()| {
                x
              //^ ()
            };
        }
    """)

    //language=RUST
    fun testFunctionCall() = testExpr("""
        struct S;

        fn new() -> S { S }

        fn main() {
            let x = new();
            x;
          //^ S
        }
    """)

    //language=RUST
    fun testUnitFunctionCall() = testExpr("""
        fn foo() {}
        fn main() {
            let x = foo();
            x;
          //^ ()
        }
    """)

    //language=RUST
    fun testStaticMethodCall() = testExpr("""
        struct S;
        struct T;
        impl S { fn new() -> T { T } }

        fn main() {
            let x = S::new();
            x;
          //^ T
        }
    """)

    //language=RUST
    fun testRefPattern() = testExpr("""
        struct Vec;

        fn bar(vr: &Vec) {
            let &v = vr;
            v;
          //^ Vec
        }
    """)

    //language=RUST
    fun testMutRefPattern() = testExpr("""
        struct Vec;

        fn bar(vr: &mut Vec) {
            let &v = vr;
            v;
          //^ <unknown>
        }
    """)

    //language=RUST
    fun testTupleOutOfBounds() = testExpr("""
        fn main() {
            let (_, _, x) = (1, 2);
            x
          //^ <unknown>
        }
    """)

}

