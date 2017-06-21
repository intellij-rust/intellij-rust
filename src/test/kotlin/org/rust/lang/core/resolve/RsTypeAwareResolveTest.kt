/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.resolve

import junit.framework.AssertionFailedError

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

    fun `test trait impl for ()`() = checkByCode("""
        trait T { fn foo(&self){} }
                   //X
        impl T for () {}
        fn main() {
            let a = ();
            a.foo()
            //^
        }
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

    fun `test trait overridden default method`() = checkByCode("""
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

    fun `test can't import methods`() = checkByCode("""
        mod m {
            pub enum E {}

            impl E {
                pub fn foo() {}
            }
        }

        use self::m::E::foo;
                        //^ unresolved
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

    fun `test string slice resolve`() = checkByCode("""
        impl<T> &str {
            fn foo(&self) {}
              //X
        }

        fn main() {
            "test".foo();
                  //^
        }
    """)

    fun `test slice resolve`() = checkByCode("""
        impl<T> [T] {
            fn foo(&self) {}
              //X
        }

        fn main() {
            let x: &[i32];
            x.foo();
             //^
        }
    """)

    fun `test slice resolve UFCS`() = expect<AssertionFailedError> {
        checkByCode("""
        impl<T> [T] {
            fn foo(&self) {}
              //X
        }

        fn main() {
            let x: &[i32];
            <[i32]>::foo(x);
                    //^
        }
    """)
    }

    fun `test array coercing to slice resolve`() = checkByCode("""
        impl<T> [T] {
            fn foo(&self) {}
              //X
        }

        fn main() {
            let x: [i32; 1];
            x.foo();
             //^
        }
    """)

    // https://github.com/intellij-rust/intellij-rust/issues/1269
    fun `test tuple field`() = checkByCode("""
        struct Foo;
        impl Foo {
            fn foo(&self) { unimplemented!() }
              //X
        }
        fn main() {
            let t = (1, Foo());
            t.1.foo();
               //^
        }
    """)

    fun `test array to slice`() = checkByCode("""
        struct Foo;
        impl Foo {
            fn foo(&self) { unimplemented!() }
              //X
        }
        fn foo<T>(xs: &[T]) -> T { unimplemented!() }
        fn main() {
            let x = foo(&[Foo(), Foo()]);
            x.foo()
             //^
        }
    """)

    fun `test tuple paren cast 1`() = checkByCode("""
        struct Foo;
        impl Foo { fn foo(&self) {} }
                     //X
        fn main() {
            let foo = unimplemented!() as (Foo);
            foo.foo();
        }      //^
    """)

    fun `test tuple paren cast 2`() = checkByCode("""
        struct Foo;
        impl Foo { fn foo(&self) {} }

        fn main() {
            let foo = unimplemented!() as (Foo,);
            foo.foo();
        }      //^ unresolved
    """)

    // https://github.com/intellij-rust/intellij-rust/issues/1549
    fun `test Self type in assoc function`() = checkByCode("""
        struct Foo;
        impl Foo {
            fn new() -> Self { unimplemented!() }
            fn bar(&self) {}
              //X
        }
        fn main() {
            let foo = Foo::new();
            foo.bar();
               //^
        }
    """)

    fun `test incomplete dot expr`() = checkByCode("""
        struct Foo;
        impl Foo {
            fn foo(&self) {}
              //X
        }
        fn main() {
            Foo.foo().
               //^
        }
    """)
}
