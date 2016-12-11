package org.rust.lang.core.resolve

class RustTypeAwareResolveTestCase : RustResolveTestCaseBase() {
    override val dataPath = "org/rust/lang/core/resolve/fixtures/type_aware"

    fun testSelfMethodCallExpr() = checkByCode("""
        struct S;

        impl S {
            fn bar(self) { }
              //X

            fn foo(self) { self.bar() }
                              //^
        }
    """)

    fun testMethodCallExpr1() = checkByCode("""
        struct S;

        impl S {
            fn bar(&self) {}
             //X
            fn foo(&self) {
                let s: S = S;

                s.bar();
                //^
            }
        }
    """)

    fun testMethodCallExpr2() = checkByCode("""
        enum S { X }

        impl S {
            fn bar(&self) {}
              //X
            fn foo(&self) {
                let s = S::X;

                s.bar();
                //^
            }
        }
    """)

    fun testMethodCallOnTraitObject() = checkByCode("""
        trait T {
            fn virtual_function(&self) {}
                //X
        }

        fn call_virtually(obj: &T) {
            obj.virtual_function()
                //^
        }
    """)

    fun testSelfFieldExpr() = checkByCode("""
        struct S { x: f32 }
                 //X

        impl S {
            fn foo(&self) { self.x; }
                               //^
        }
    """)

    fun testFieldExpr() = checkByCode("""
        struct S { x: f32 }
                 //X

        impl S {
            fn foo(&self) {
                let s: S = S { x: 0. };
                s.x;
                //^
            }
        }
    """)

    fun testTupleFieldExpr() = checkByCode("""
        struct T;
        impl T { fn foo(&self) {} }
                  //X

        struct S(T);

        impl S {
            fn foo(&self) {
                let s: S = S(92.0);
                s.0.foo();
                   //^
            }
        }
    """)

    fun testTupleFieldExprOutOfBounds() = checkByCode("""
        struct S(f64);

        impl S {
            fn foo(&self) {
                let s: S = S(92.0);
                s.92;
                //^ unresolved
            }
        }
    """)

    fun testNestedFieldExpr() = checkByCode("""
        struct Foo { bar: Bar }

        struct Bar { baz: i32 }
                    //X

        fn main() {
            let foo = Foo { bar: Bar { baz: 92 } };
            foo.bar.baz;
                  //^
        }
    """)


    fun testLetDeclCallExpr() = checkByCode("""
        struct S { x: f32 }
                 //X

        fn bar() -> S {}

        impl S {
            fn foo(&self) {
                let s = bar();

                s.x;
                //^
            }
        }
    """)

    fun testLetDeclMethodCallExpr() = checkByCode("""
        struct S { x: f32 }
                 //X

        impl S {
            fn bar(&self) -> S {}
            fn foo(&self) {
                let s = self.bar();
                s.x;
                //^
            }
        }
    """)

    fun testLetDeclPatIdentExpr() = checkByCode("""
        struct S { x: f32 }
                 //X

        impl S {
            fn foo(&self) {
                let s = S { x: 0. };

                s.x;
                //^
            }
        }
    """)

    fun testLetDeclPatTupExpr() = checkByCode("""
        struct S { x: f32 }
                 //X
        impl S {
            fn foo(&self) {
                let (_, s) = ((), S { x: 0. });

                s.x;
                //^
            }
        }
    """)

    fun testLetDeclPatStructExpr() = checkByCode("""
        struct S { x: f32 }

        impl S {
            fn foo(&self) {
                let S { x: x } = S { x: 0. };
                         //X
                x;
              //^
            }
        }
    """)

    fun testLetDeclPatStructExprComplex() = checkByCode("""
        struct S { x: f32 }
                 //X

        struct X { s: S }

        impl S {
            fn foo(&self) {
                let X { s: f } = X { s: S { x: 0. } };
                f.x;
                //^
            }
        }
    """)

    fun testStaticFnFromInherentImpl() = checkByCode("""
        struct S;

        impl S { fn test() { } }
                    //X

        fn main() { S::test(); }
                      //^
    """)

    fun testNonStaticFnFromInherentImpl() = checkByCode("""
        struct S;

        impl S { fn test(&self) { } }
                    //X

        fn main() {
            let s = S;
            S::test(&s);
               //^
        }
    """)

    fun testHiddenInherentImpl() = checkByCode("""
        struct S;

        fn main() {
            let s: S = S;
            s.transmogrify();
                //^
        }

        mod hidden {
            use super::S;

            impl S { pub fn transmogrify(self) -> S { S } }
                            //X
        }
    """)

    fun testWrongInherentImpl() = checkByCode("""
        struct S;

        fn main() {
            let s: S = S;

            s.transmogrify();
                //^ unresolved
        }

        mod hidden {
            struct S;

            impl S { pub fn transmogrify(self) -> S { S } }
        }
    """)

    fun testImplGenericsStripped() = checkByCode("""
        struct S<FOO> { field: FOO }

        fn main() {
            let s: S = S;

            s.transmogrify();
                //^
        }

        impl<BAR> S<BAR> {
            fn transmogrify(&self) { }
                //X
        }
    """)


    fun testNonInherentImpl1() = checkByCode("""
        struct S;

        mod m {
            trait T { fn foo(); }
        }

        mod hidden {
            use super::S;
            use super::m::T;

            impl T for S { fn foo() {} }
                            //X
        }

        fn main() {
            use m::T;

            let _ = S::foo();
                     //^
        }
    """)

    //FIXME: should resolve to non ref!
    fun testNonInherentImpl2() = checkByCode("""
        trait T { fn foo(&self) { println!("Hello"); } }

        struct S;

        impl T for S { fn foo(&self) { println!("non ref"); } }

        impl<'a> T for &'a S { fn foo(&self) { println!("ref"); } }
                                 //X

        fn main() {
            let x: &S = &S;
            x.foo()
              //^
        }
    """)

    fun testGenericParamMethodCall() = checkByCode("""
        trait Spam { fn eggs(&self); }
                        //X

        fn foo<T: Spam>(x: T) { x.eggs() }
                                  //^
    """)

    fun testGenericParamMethodCallWhere() = checkByCode("""
        trait Spam { fn eggs(&self); }
                        //X

        fn foo<T>(x: T) where T: Spam { x.eggs() }
                                          //^
    """)
}
