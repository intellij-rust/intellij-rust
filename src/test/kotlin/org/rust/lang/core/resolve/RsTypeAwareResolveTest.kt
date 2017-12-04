/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.resolve

import junit.framework.AssertionFailedError

class RsTypeAwareResolveTest : RsResolveTestBase() {
    fun `test self method call expr`() = checkByCode("""
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

    fun `test trait impl for various types`() {
        for (type in listOf("bool", "char", "&str", "u32", "f32", "f64", "()", "(i32)", "(i16,)", "(u32, u16)",
            "[u8; 1]", "&[u16]", "*const u8", "*const i8", "fn(u32) -> u8", "!")) {
            checkByCode("""
            trait T { fn foo(&self) {} }

            impl T for $type {
                fn foo(&self) {}
            }      //X

            fn test(s: $type) {
                s.foo()
            }    //^
        """)
        }
    }

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

    fun `test method reference`() = checkByCode("""
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

    fun `test method call on trait object`() = stubOnlyResolve("""
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

    fun `test method inherent vs trait conflict`() = checkByCode("""
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

    fun `test self field expr`() = checkByCode("""
        struct S { x: f32 }
                 //X

        impl S {
            fn foo(&self) { self.x; }
                               //^
        }
    """)

    fun `test field expr`() = stubOnlyResolve("""
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

    fun `test tuple field expr`() = checkByCode("""
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

    fun `test tuple field expr out of bounds`() = checkByCode("""
        struct S(f64);

        impl S {
            fn foo(&self) {
                let s: S = S(92.0);
                s.92;
                //^ unresolved
            }
        }
    """)

    fun `test tuple field expr suffix`() = checkByCode("""
        struct S(f64);

        impl S {
            fn foo(&self) {
                let s: S = S(92.0);
                s.0u32;
                //^ unresolved
            }
        }
    """)

    fun `test nested field expr`() = checkByCode("""
        struct Foo { bar: Bar }

        struct Bar { baz: i32 }
                    //X

        fn main() {
            let foo = Foo { bar: Bar { baz: 92 } };
            foo.bar.baz;
                  //^
        }
    """)

    fun `test let decl call expr`() = checkByCode("""
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

    fun `test let decl method call expr`() = checkByCode("""
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

    fun `test let decl pat ident expr`() = checkByCode("""
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

    fun `test let decl pat tup expr`() = checkByCode("""
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

    fun `test let decl pat struct expr`() = checkByCode("""
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

    fun `test let decl pat struct expr complex`() = checkByCode("""
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

    fun `test associated fn from inherent impl`() = checkByCode("""
        struct S;

        impl S { fn test() { } }
                    //X

        fn main() { S::test(); }
                      //^
    """)

    fun `test associated function inherent vs trait conflict`() = checkByCode("""
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

    fun `test hidden inherent impl`() = checkByCode("""
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

    fun `test wrong inherent impl`() = checkByCode("""
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

    fun `test non inherent impl 1`() = checkByCode("""
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

    fun `test self implements trait`() = checkByCode("""
        trait Foo {
            fn foo(&self);
              //X

            fn bar(&self) { self.foo(); }
                                //^
        }
    """)

    fun `test self implements trait from bound`() = checkByCode("""
        trait Bar {
            fn bar(&self);
             //X
        }
        trait Foo : Bar {
            fn foo(&self) { self.bar(); }
                                //^
        }
    """)

    fun `test match enum tuple variant`() = checkByCode("""
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

    fun `test static`() = checkByCode("""
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

    fun `test slice resolve UFCS`() = expect<IllegalStateException> {
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

    fun `test impl trait for mutable reference`() = checkByCode("""
        struct Foo;
        trait T { fn foo(self); }
        impl<'a> T for &'a Foo { fn foo(self) {} }
        impl<'a> T for &'a mut Foo { fn foo(self) {} }
                                       //X

        fn main() {
            let foo = &mut Foo {};
            foo.foo();
        }      //^
    """)

    fun `test impl trait for mutable pointer`() = checkByCode("""
        struct Foo;
        trait T { fn foo(self); }
        impl T for *const Foo { fn foo(self) {} }
        impl T for *mut Foo { fn foo(self) {} }
                                //X

        fn main() {
            let foo = &mut Foo {} as *mut Foo;
            foo.foo();
        }      //^
    """)

    fun `test resolve UFCS method call`() = checkByCode("""
        struct S;
        trait T { fn foo(&self); }
        impl T for S { fn foo(&self) {} }
                        //X
        fn main() {
            T::foo(&S);
        }    //^
    """)

    fun `test resolve trait associated function`() = checkByCode("""
        struct S;
        trait T { fn foo() -> Self; }
        impl T for S { fn foo() -> Self { unimplemented!() } }
                        //X
        fn main() {
            let a: S = T::foo();
        }               //^
    """)

    fun `test resolve impl Trait method`() = checkByCode("""
        trait Trait {
            fn bar(self);
        }     //X
        fn foo() -> impl Trait { unimplemented!() }
        fn main() {
            foo().bar();
        }       //^
    """)

    fun `test resolve impl Trait1+Trait2 method of Trait1`() = checkByCode("""
        trait Trait1 {
            fn bar(self);
        }     //X
        trait Trait2 { fn baz(self); }
        fn foo() -> impl Trait1+Trait2 { unimplemented!() }
        fn main() {
            foo().bar();
                //^
        }
    """)

    fun `test resolve impl Trait1+Trait2 method of Trait2`() = checkByCode("""
        trait Trait1 { fn bar(self); }
        trait Trait2 {
            fn baz(self);
        }     //X
        fn foo() -> impl Trait1+Trait2 { unimplemented!() }
        fn main() {
            foo().baz();
                //^
        }
    """)
}
