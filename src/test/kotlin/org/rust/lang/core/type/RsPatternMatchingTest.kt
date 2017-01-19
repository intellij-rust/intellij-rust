package org.rust.lang.core.type

class RsPatternMatchingTest : RsTypificationTestBase() {
    fun testIfLetPattern() = testExpr("""
        enum E { L(i32), R(bool) }
        fn main() {
            let _ = if let E::L(x) = E::R(false) { x } else { x };
                                                 //^ i32
        }
    """)

    fun testWhileLetPattern() = testExpr("""
        enum E { L(i32), R(bool) }
        fn main() {
            let e = E::L(92);
            while let E::R(x) = e {
                x
            } //^ bool
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

    fun testBracedEnumVariant() = testExpr("""
        enum E { S { foo: i32 }}

        fn main() {
            let x: E = unimplemented!();
            match x { E::S { foo } => foo }
        }                           //^ i32
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
          //^ Vec
        }
    """)

    fun testTupleOutOfBounds() = testExpr("""
        fn main() {
            let (_, _, x) = (1, 2);
            x
          //^ <unknown>
        }
    """)

    fun testLiteralPattern() = testExpr("""
    fn main() {
        let x: (i32, String) = unimplemented!();
        match x { (x, "foo") => x }
    }                         //^ i32
    """)

}
