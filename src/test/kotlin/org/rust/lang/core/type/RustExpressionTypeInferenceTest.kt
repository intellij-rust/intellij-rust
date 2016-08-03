package org.rust.lang.core.type

class RustExpressionTypeInferenceTest : RustTypificationTestBase() {
    fun testFunctionCall() = testExpr("""
        struct S;

        fn new() -> S { S }

        fn main() {
            let x = new();
            x;
          //^ S
        }
    """)

    fun testUnitFunctionCall() = testExpr("""
        fn foo() {}
        fn main() {
            let x = foo();
            x;
          //^ ()
        }
    """)

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

    fun testBlockExpr() = testExpr("""
        struct S;

        fn foo() -> S {}
        fn main() {
            let x = {
                foo()
            };

            x
          //^ S
        }
    """)

    fun testUnitBlockExpr() = testExpr("""
        struct S;

        fn foo() -> S {}
        fn main() {
            let x = {
                foo();
            };

            x
          //^ ()
        }
    """)

    fun testEmptyBlockExpr() = testExpr("""
        fn main() {
            let x = {};
            x
          //^ ()
        }
    """)

    fun testTypeParameters() = testExpr("""
        fn foo<FOO>(foo: FOO) {
            let bar = foo;
            bar
          //^ FOO
        }
    """)

    fun testUnitIf() = testExpr("""
        fn main() {
            let x = if true { 92 };
            x
          //^ ()
        }
    """)

    fun testIf() = testExpr("""
        fn main() {
            let x = if true { 92 } else { 62 };
            x
          //^ i32
        }
    """)

    fun testLoop() = testExpr("""
        fn main() {
            let x = loop { break; };
            x
          //^ ()
        }
    """)

    fun testWhile() = testExpr("""
        fn main() {
            let x = while false { 92 };
            x
          //^ ()
        }
    """)

    fun testParenthesis() = testExpr("""
        fn main() {
            (false);
          //^ bool
        }
    """)

    fun testEnumVariantA() = testExpr("""
        enum E { A(i32), B { val: bool }, C }
        fn main() {
            (E::A(92))
          //^ E
        }
    """)

    fun testEnumVariantB() = testExpr("""
        enum E { A(i32), B { val: bool }, C }
        fn main() {
            (E::B { val: 92 })
          //^ E
        }
    """)
    
    fun testEnumVariantC() = testExpr("""
        enum E { A(i32), B { val: bool }, C }
        fn main() {
            (E::C)
          //^ E
        }
    """)

    // Ideally these two should be handled by separate type/value namespaces
    fun testNoStackOverflow1() = testExpr("""
        pub struct P<T: ?Sized> { ptr: Box<T> }

        #[allow(non_snake_case)]
        pub fn P<T: 'static>(value: T) -> P<T> {
            P { ptr: Box::new(value) }
        }

        fn main() {
            let x = P(92);
            x
          //^ fn(T) -> <unknown>
        }
    """)

    fun testNoStackOverflow2() = testExpr("""
        fn foo(S: S){
            let x = S;
            x.foo()
              //^ <unknown>
        }
    """)

}

