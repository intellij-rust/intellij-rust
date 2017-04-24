package org.rust.lang.core.resolve

class RsTypeAwareResolveTest : RsResolveTestBase() {
    fun testSelfMethodCallExpr() = checkByCode("""
        struct S;

        impl S {
            fn bar(self) { }
              //X

            fn foo(self) { self.bar() }
                              //^
        }
    """)

    fun `test trait impl method`() = checkByCode("""
        trait T { fn foo(&self); }
        struct S;
        impl T for S { fn foo(&self) {} }
                         //X
        fn foo(s: S) {
            s.foo()
        }    //^
    """)

    fun `test trait default method`() = checkByCode("""
        trait T { fn foo(&self) {} }
                    //X
        struct S;
        impl T for S { }

        fn foo(s: S) {
            s.foo()
        }    //^
    """)

    fun `test trait overriden default method`() = checkByCode("""
        trait T { fn foo(&self) {} }

        struct S;
        impl T for S { fn foo(&self) {} }
                         //X
        fn foo(s: S) {
            s.foo()
        }    //^
    """)

    fun testMethodReference() = checkByCode("""
    //- main.rs
        mod x;
        use self::x::Stdin;

        fn main() {
            Stdin::read_line;
                     //^
        }

    //- x.rs
        pub struct Stdin { }

        impl Stdin {
            pub fn read_line(&self) { }
                   //X
        }
    """)

    fun testMethodCallOnTraitObject() = stubOnlyResolve("""
    //- main.rs
        mod aux;
        use aux::T;

        fn call_virtually(obj: &T) { obj.virtual_function() }
                                                //^ aux.rs

    //- aux.rs
        trait T {
            fn virtual_function(&self) {}
        }
    """)

    fun testMethodInherentVsTraitConflict() = checkByCode("""
        struct Foo;
        impl Foo {
            fn bar(&self) {}
               //X
        }

        trait Bar {
            fn bar(&self);
        }
        impl Bar for Foo {
            fn bar(&self) {}
        }

        fn main() {
            let foo = Foo;
            foo.bar();
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

    fun testFieldExpr() = stubOnlyResolve("""
    //- main.rs
        mod aux;
        use aux::S;
        fn main() {
            let s: S = S { x: 0. };
            s.x;
            //^ aux.rs
        }

    //- aux.rs
        struct S { x: f32 }
    """)

    fun testTupleFieldExpr() = checkByCode("""
        struct T;
        impl T { fn foo(&self) {} }
                  //X

        struct S(T);

        impl S {
            fn foo(&self) {
                let s = S(92.0);
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

    fun testTupleFieldExprSuffix() = checkByCode("""
        struct S(f64);

        impl S {
            fn foo(&self) {
                let s: S = S(92.0);
                s.0u32;
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

    fun testAssociatedFnFromInherentImpl() = checkByCode("""
        struct S;

        impl S { fn test() { } }
                    //X

        fn main() { S::test(); }
                      //^
    """)

    fun testAssociatedFunctionInherentVsTraitConflict() = checkByCode("""
        struct Foo;
        impl Foo {
            fn bar() {}
               //X
        }

        trait Bar {
            fn bar();
        }
        impl Bar for Foo {
            fn bar() {}
        }

        fn main() {
            Foo::bar();
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

    fun testSelfImplementsTrait() = checkByCode("""
        trait Foo {
            fn foo(&self);
              //X

            fn bar(&self) { self.foo(); }
                                //^
        }
    """)

    fun testSelfImplementsTraitFromBound() = checkByCode("""
        trait Bar {
            fn bar(&self);
             //X
        }
        trait Foo : Bar {
            fn foo(&self) { self.bar(); }
                                //^
        }
    """)

    fun testChainedBounds() = checkByCode("""
        trait A { fn foo(&self) {} }
                    //X
        trait B : A {}
        trait C : B {}
        trait D : C {}

        struct X;
        impl D for X {}

        fn bar<T: D>(x: T) { x.foo() }
                              //^
    """)

    fun testChainedBoundsCycle() = checkByCode("""
        trait A: B {}
        trait B: A {}

        fn bar<T: A>(x: T) { x.foo() }
                              //^ unresolved
    """)

    fun testCantImportMethods() = checkByCode("""
        mod m {
            pub enum E {}

            impl E {
                pub fn foo() {}
            }
        }

        use self::m::E::foo;
                        //^ unresolved
    """)

    fun testResultTryOperator() = checkByCode("""
        enum Result<T, E> { Ok(T), Err(E)}
        struct S { field: u32 }
                    //X
        fn foo() -> Result<S, ()> { unimplemented!() }

        fn main() {
            let s = foo()?;
            s.field;
            //^
        }
    """)

    fun testResultUnwrap() = checkByCode("""
        enum Result<T, E> { Ok(T), Err(E)}

        impl<T, E: fmt::Debug> Result<T, E> {
            pub fn unwrap(self) -> T { unimplemented!() }
        }

        struct S { field: u32 }
                    //X
        fn foo() -> Result<S, ()> { unimplemented!() }

        fn main() {
            let s = foo().unwrap();
            s.field;
            //^
        }
    """)

    fun testResultTryOperatorWithAlias() = checkByCode("""
        enum Result<T, E> { Ok(T), Err(E)}

        mod io {
            pub struct IoError;
            pub type IoResult<T> = super::Result<T, IoError>;

            pub struct S { field: u32 }
                          //X

            pub fn foo() -> IoResult<S> { unimplemented!() }

        }

        fn main() {
            let s = io::foo()?;
            s.field;
              //^
        }
    """)

    fun testResultUnwrapWithAlias() = checkByCode("""
        enum Result<T, E> { Ok(T), Err(E)}
        impl<T, E: fmt::Debug> Result<T, E> {
            pub fn unwrap(self) -> T { unimplemented!() }
        }

        mod io {
            pub struct Error;
            pub type Result<T> = super::Result<T, Error>;
        }

        struct S { field: u32 }
                    //X
        fn foo() -> io::Result<S> { unimplemented!() }

        fn main() {
            let s = foo().unwrap();
            s.field;
              //^
        }
    """)

    fun testMatchEnumTupleVariant() = checkByCode("""
        enum E { V(S) }
        struct S;

        impl S { fn frobnicate(&self) {} }
                    //X
        impl E {
            fn foo(&self) {
                match *self {
                    E::V(ref s) => s.frobnicate()
                }                   //^
            }
        }
    """)

    fun testStatic() = checkByCode("""
        struct S { field: i32 }
                    //X
        const FOO: S = S { field: 92 };

        fn main() {
            FOO.field;
        }       //^
    """)

    fun `test string slice resolve`()= checkByCode("""
        impl<T> &str {
            fn foo(&self) {}
              //X
        }

        fn main() {
            "test".foo();
                    //^
        }
    """)

    fun `test slice resolve`()= checkByCode("""
        impl<T> [T] {
            fn foo(&self) {}
              //X
        }

        fn main() {
            let x : [i32];
            x.foo()
             //^
        }
    """)

    fun `test iterator for loop resolve`() = checkByCode("""
        trait Iterator { type Item; fn next(&mut self) -> Option<Self::Item>; }

        struct S;
        impl S { fn foo(&self) {} }
                  //X
        struct I;
        impl Iterator for I {
            type Item = S;
            fn next(&mut self) -> Option<S> { None }
        }

        fn main() {
            for s in I {
                s.foo();
            }    //^
        }
    """)

    fun `test into iterator for loop resolve`() = checkByCode("""
        trait Iterator { type Item; fn next(&mut self) -> Option<Self::Item>; }
        trait IntoIterator {
            type Item;
            type IntoIter: Iterator<Item=Self::Item>;
            fn into_iter(self) -> Self::IntoIter;
        }

        struct S;
        impl S { fn foo(&self) {} }
                  //X
        struct I;
        impl Iterator for I {
            type Item = S;
            fn next(&mut self) -> Option<S> { None }
        }

        struct II;
        impl IntoIterator for II {
            type Item = S;
            type IntoIter = I;
            fn into_iter(self) -> Self::IntoIter { I }
        }

        fn main() {
            for s in II {
                s.foo()
            }   //^
        }
    """)

    fun `test method in specialized trait impl for struct`() = checkByCode("""
        trait Tr { fn some_fn(&self); }
        struct S<T> { value: T }
        impl Tr for S<u8> {
            fn some_fn(&self) { }
        }
        impl Tr for S<u16> {
            fn some_fn(&self) { }
             //X
        }
        fn main() {
            let v = S {value: 5u16};
            v.some_fn();
            //^
        }
    """)

    fun `test method in specialized trait impl for struct 2`() = checkByCode("""
        trait Tr { fn some_fn(&self); }
        struct S<T1, T2> { value1: T1, value2: T2 }
        impl Tr for S<u8, u8> {
            fn some_fn(&self) { }
        }
        impl Tr for S<u16, u8> {
            fn some_fn(&self) { }
             //X
        }
        impl Tr for S<u8, u16> {
            fn some_fn(&self) { }
        }
        impl Tr for S<u16, u16> {
            fn some_fn(&self) { }
        }
        fn main() {
            let v = S {value1: 5u16, value2: 5u8};
            v.some_fn();
            //^
        }
    """)

    fun `test method in specialized trait impl for tuple struct`() = checkByCode("""
        trait Tr { fn some_fn(&self); }
        struct S<T> (T);
        impl Tr for S<u8> {
            fn some_fn(&self) { }
        }
        impl Tr for S<u16> {
            fn some_fn(&self) { }
             //X
        }
        fn main() {
            let v = S (5u16);
            v.some_fn();
            //^
        }
    """)

    fun `test method in specialized trait impl for enum`() = checkByCode("""
        trait Tr { fn some_fn(&self); }
        enum S<T> { Var1{value: T}, Var2 }
        impl Tr for S<u8> {
            fn some_fn(&self) { }
        }
        impl Tr for S<u16> {
            fn some_fn(&self) { }
             //X
        }
        fn main() {
            let v = S::Var1 {value: 5u16};
            v.some_fn();
            //^
        }
    """)

    fun `test method in specialized trait impl for tuple enum`() = checkByCode("""
        trait Tr { fn some_fn(&self); }
        enum S<T> { Var1(T), Var2  }
        impl Tr for S<u8> {
            fn some_fn(&self) { }
        }
        impl Tr for S<u16> {
            fn some_fn(&self) { }
             //X
        }
        fn main() {
            let v = S::Var1 (5u16);
            v.some_fn();
            //^
        }
    """)

    fun `test method in specialized impl for struct`() = checkByCode("""
        struct S<T> { value: T }
        impl S<u8> {
            fn some_fn(&self) { }
        }
        impl S<u16> {
            fn some_fn(&self) { }
             //X
        }
        fn main(v: S<u16>) {
            v.some_fn();
            //^
        }
    """)

    fun `test trait bound not satisfied`() = checkByCode("""
        trait Tr1 { fn some_fn(&self) {} }
        trait Bound1 {}
        trait Bound2 {}
        struct S<T> { value: T }
        impl<T: Bound1> Tr1 for S<T> {}
        struct S0;
        impl Bound2 for S0 {}
        fn main(v: S<S0>) {
            v.some_fn();
            //^ unresolved
        }
    """)

    fun `test trait bound satisfied for struct`() = checkByCode("""
        trait Tr1 { fn some_fn(&self) {} }
        trait Tr2 { fn some_fn(&self) {} }
                     //X
        trait Bound1 {}
        trait Bound2 {}
        struct S<T> { value: T }
        impl<T: Bound1> Tr1 for S<T> {}
        impl<T: Bound2> Tr2 for S<T> {}
        struct S0;
        impl Bound2 for S0 {}
        fn main(v: S<S0>) {
            v.some_fn();
            //^
        }
    """)

    fun `test trait bound satisfied for trait`() = checkByCode("""
//        #[lang = "sized"]
//        trait Sized {}
        trait Tr1 { fn some_fn(&self) {} }
        trait Tr2 { fn some_fn(&self) {} }
                     //X
        trait Bound1 {}
        trait Bound2 {}
        trait ChildOfBound2 : Bound2 {}
        struct S<T : ?Sized> { value: T }
        impl<T: Bound1 + ?Sized> Tr1 for S<T> { }
        impl<T: Bound2 + ?Sized> Tr2 for S<T> { }
        fn f(v: &S<ChildOfBound2>) {
            v.some_fn();
            //^
        }
    """)

    fun `test trait bound satisfied for other bound`() = checkByCode("""
        trait Tr1 { fn some_fn(&self) {} }
        trait Tr2 { fn some_fn(&self) {} }
                     //X
        trait Bound1 {}
        trait Bound2 {}
        struct S<T> { value: T }
        impl<T: Bound1> Tr1 for S<T> { }
        impl<T: Bound2> Tr2 for S<T> { }

        struct S1<T> { value: T }
        impl<T: Bound2> S1<T> {
            fn f(&self, t: S<T>) {
                t.some_fn();
                //^
            }
        }
    """)

    fun `test impl not resolved by accident if struct name is the same as generic type`() = checkByCode("""
        struct S;
        trait Tr1{}
        trait Tr2{ fn some_fn(&self) {} }
        impl<S: Tr1> Tr2 for S {}
        fn main(v: S) {
            v.some_fn();
            //^ unresolved
        }
    """)
}
