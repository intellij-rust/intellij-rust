package org.rust.lang.core.type

class RustPatternMatchingTest : RustTypificationTestBase() {
    fun testIfLetPattern() = testExpr("""
        fn main() {
            let _ = if let Some(x) = Some(92i32) { x } else { x };
                                                 //^ <unknown>
        }
    """)

    fun testLetTypeAscription() = testExpr("""
        struct S;
        struct T;

        fn main() {
            let (x, _): (S, T) = unimplemented!();
            x;
          //^ S
        }
    """)

    fun testLetInitExpr() = testExpr("""
        struct S;
        struct T;

        fn main() {
            let (_, x) = (S, T);
            x;
          //^ T
        }
    """)

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

    fun testFnArgumentPattern() = testExpr("""
        struct S;
        struct T;

        fn main((x, _): (S, T)) {
            x;
          //^ S
        }
    """)

    fun testClosureArgument() = testExpr("""
        fn main() {
            let _ = |x: ()| {
                x
              //^ ()
            };
        }
    """)

    fun testRefPattern() = testExpr("""
        struct Vec;

        fn bar(vr: &Vec) {
            let &v = vr;
            v;
          //^ Vec
        }
    """)

    fun testMutRefPattern() = testExpr("""
        struct Vec;

        fn bar(vr: &mut Vec) {
            let &v = vr;
            v;
          //^ <unknown>
        }
    """)

    fun testTupleOutOfBounds() = testExpr("""
        fn main() {
            let (_, _, x) = (1, 2);
            x
          //^ <unknown>
        }
    """)

}
