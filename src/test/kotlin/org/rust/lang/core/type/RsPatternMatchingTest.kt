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

    fun testRefPattern2() = testExpr("""
        struct Vec;

        fn bar(vr: Vec) {
            let ref v = vr;
            v;
          //^ &Vec
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

    fun testMutRefPattern2() = testExpr("""
        struct Vec;

        fn bar(vr: Vec) {
            let ref mut v = vr;
            v;
          //^ &mut Vec
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

    fun testGenericTupleStructPattern() = testExpr("""
        struct S<T>(T);
        fn main() {
            let s = S(123);
            if let S(x) = s { x }
                            //^ i32
        }
    """)

    fun testGenericStructPattern() = testExpr("""
        struct S<T> { s: T }
        fn main() {
            let s = S { s: 123 };
            match s { S { s: x } => x }
                                  //^ i32
        }
    """)

    fun testGenericEnumTupleStructPattern() = testExpr("""
        enum E<T1, T2> { L(T1), R { r: T2 } }
        fn foo(e: E<i32, bool>) {
            match e {
                E::L(x) => x,
                         //^ i32
                E::R { r: x } => x
            }
        }
    """)

    fun testGenericEnumStructPattern() = testExpr("""
        enum E<T1, T2> { L(T1), R { r: T2 } }
        fn foo(e: E<i32, bool>) {
            match e {
                E::L(x) => x,
                E::R { r: x } => x
                               //^ bool
            }
        }
    """)
}
