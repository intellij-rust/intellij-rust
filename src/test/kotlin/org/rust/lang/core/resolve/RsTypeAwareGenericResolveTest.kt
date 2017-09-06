/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.resolve

import org.rust.lang.core.psi.ext.ArithmeticOp

class RsTypeAwareGenericResolveTest : RsResolveTestBase() {
    fun testFn() = checkByCode("""
        fn foo<T>() -> T {
             //X
            let x: T = unimplemented!();
                 //^
            }
    """)

    fun testImplMethod() = checkByCode("""
        struct S;

        impl S {
            fn foo<Param>(
                    //X
                param: Param
            ) {}      //^
        }
    """)

    fun testTraitMethod() = checkByCode("""
        trait T {
            fn f<Param>()
                //X
                -> Param;
                    //^
        }
    """)

    fun testStruct() = checkByCode("""
        struct S<Thing> {
                //X
            field: Vec<Thing>
                      //^
        }
    """)

    fun testEnum() = checkByCode("""
        enum E<T> {
             //X
            V(T)
            //^
        }
    """)

    fun testTrait() = checkByCode("""
        trait T<Param> {
                //X
            fn new() -> Param;
                        //^
        }
    """)

    fun testImpl() = checkByCode("""
        struct S<T> { field: T }

        impl<T> S<T> {
           //X
            fn foo() -> T { }
                      //^
        }
    """)

    fun testTypeAlias() = checkByCode("""
        use std::result;

        pub type Result<T> =
                      //X
            result::Result<T, Error>;
                         //^
    """)

    fun testNoLeakInEnum() = checkByCode("""
        enum E<T> { X }

        fn main() { let _ = E::T; }
                             //^ unresolved
    """)

    fun testNoLeakInStruct() = checkByCode("""
        struct S<T>;

        fn main() { let _: S::T = unreachable!(); }
                            //^ unresolved
    """)

    fun `test don't check type parameter name when looking for impls`() = checkByCode("""
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

    fun `test method call on trait from bound`() = checkByCode("""
        trait Spam { fn eggs(&self); }
                        //X

        fn foo<T: Spam>(x: T) { x.eggs() }
                                  //^
    """)

    fun `test method call on trait from bound in where`() = checkByCode("""
        trait Spam { fn eggs(&self); }
                        //X

        fn foo<T>(x: T) where T: Spam { x.eggs() }
                                          //^
    """)

    fun `test method call on trait from bound's closure`() = checkByCode("""
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

    fun `test method call on trait with cyclic bounds`() = checkByCode("""
        trait A: B {}
        trait B: A {}

        fn bar<T: A>(x: T) { x.foo() }
                              //^ unresolved
    """)

    fun `test trait bound propagates type arguments`() = checkByCode("""
        trait I<T> {
            fn foo(&self) -> T;
        }

        struct S;
        impl S {
            fn bar(&self) {}
        }     //X

        fn baz<T: I<S>>(t: T) {
            t.foo().bar()
        }         //^
    """)

    fun `test Result unwrap`() = checkByCode("""
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

    fun `test unwrap with aliased Result`() = checkByCode("""
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

    fun `test generic function call`() = checkByCode("""
        struct S;
        impl S { fn m(&self) {} }
                  //X
        fn f<T>(t: T) -> T { t }
        fn main() {
            f(S).m();
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

    fun `test single auto deref`() = checkByCode("""
        struct A;
        struct B;
        impl B { fn some_fn(&self) { } }
                    //X
        #[lang = "deref"]
        trait Deref { type Target; }
        impl Deref for A { type Target = B; }

        fn foo(a: A) {
            a.some_fn()
            //^
        }
    """)

    fun `test multiple auto deref`() = checkByCode("""
        struct A;
        struct B;
        struct C;
        impl C { fn some_fn(&self) { } }
                    //X
        #[lang = "deref"]
        trait Deref { type Target; }
        impl Deref for A { type Target = B; }
        impl Deref for B { type Target = C; }

        fn foo(a: A) {
            a.some_fn()
            //^
        }
    """)

    fun `test recursive auto deref`() = checkByCode("""
        #[lang = "deref"]
        trait Deref { type Target; }

        struct A;
        struct B;
        struct C;

        impl C { fn some_fn(&self) { } }
                    //X

        impl Deref for A { type Target = B; }
        impl Deref for B { type Target = C; }
        impl Deref for C { type Target = A; }

        fn foo(a: A) {
            // compiler actually bails with `reached the recursion limit while auto-dereferencing B`
            a.some_fn()
            //^
        }
    """)

    fun `test indexing`() = checkByCode("""
        #[lang = "index"]
        trait Index<Idx: ?Sized> {
            type Output: ?Sized;
            fn index(&self, index: Idx) -> &Self::Output;
        }

        struct Container;
        struct Elem;
        impl Elem { fn foo(&self) {} }
                      //X

        impl Index<usize> for Container {
            type Output = Elem;
            fn index(&self, index: usize) -> &Elem { unimplemented!() }
        }

        fn bar(c: Container) {
            c[0].foo()
                //^
        }
    """)

    fun `test indexing with multiple impls`() = checkByCode("""
        #[lang = "index"]
        trait Index<Idx: ?Sized> {
            type Output: ?Sized;
            fn index(&self, index: Idx) -> &Self::Output;
        }

        struct Container;
        struct Elem1;
        impl Elem1 { fn foo(&self) {} }
        struct Elem2;
        impl Elem2 { fn foo(&self) {} }
                       //X

        impl Index<usize> for Container {
            type Output = Elem1;
            fn index(&self, index: usize) -> &Elem1 { unimplemented!() }
        }

        impl Index<f64> for Container {
            type Output = Elem2;
            fn index(&self, index: f64) -> &Elem2 { unimplemented!() }
        }

        fn bar(c: Container) {
            c[0.0].foo()
                  //^
        }
    """)

    fun `test simple generic function argument`() = checkByCode("""
        struct Foo<F>(F);
        struct Bar;
        impl Bar {
            fn bar(&self) { unimplemented!() }
              //X
        }
        fn foo<T>(xs: Foo<T>) -> T { unimplemented!() }
        fn main() {
            let x = foo(Foo(Bar()));
            x.bar();
             //^
        }
    """)

    fun `test complex generic function argument`() = checkByCode("""
        struct Foo<T1, T2>(T1, T2);
        enum Bar<T3> { V(T3) }
        struct FooBar<T4, T5>(T4, T5);
        struct S;

        impl S {
            fn bar(&self) { unimplemented!() }
              //X
        }

        fn foo<F1, F2, F3>(x: FooBar<Foo<F1, F2>, Bar<F3>>) -> Foo<F2, F3> { unimplemented!() }
        fn main() {
            let x = foo(FooBar(Foo(123, "foo"), Bar::V(S())));
            x.1.bar();
              //^
        }
    """)

    fun `test generic method argument`() = checkByCode("""
        struct Foo<F>(F);
        enum Bar<B> { V(B) }
        struct FooBar<E1, E2>(E1, E2);
        struct S;

        impl<T1> Foo<T1> {
            fn foo<T2>(&self, bar: Bar<T2>) -> FooBar<T1, T2> { unimplemented!() }
        }

        impl S {
            fn bar(&self) { unimplemented!() }
              //X
        }

        fn main() {
            let x = Foo(123).foo(Bar::V(S()));
            x.1.bar();
              //^
        }
    """)

    fun `test arithmetic operations`() {
        for ((traitName, itemName, sign) in ArithmeticOp.values()) {
            checkByCode("""
                #[lang = "$itemName"]
                pub trait $traitName<RHS=Self> {
                    type Output;
                    fn $itemName(self, rhs: RHS) -> Self::Output;
                }

                struct Foo;
                struct Bar;

                impl Bar {
                    fn bar(&self) { unimplemented!() }
                      //X
                }

                impl $traitName<i32> for Foo {
                    type Output = Bar;
                    fn $itemName(self, rhs: i32) -> Bar { unimplemented!() }
                }

                fn foo(lhs: Foo, rhs: i32) {
                    let x = lhs $sign rhs;
                    x.bar()
                     //^
                }
            """)
        }
    }

    fun `test arithmetic operations with multiple impls`() {
        for ((traitName, itemName, sign) in ArithmeticOp.values()) {
            checkByCode("""
                #[lang = "$itemName"]
                pub trait $traitName<RHS=Self> {
                    type Output;
                    fn $itemName(self, rhs: RHS) -> Self::Output;
                }

                struct Foo;
                struct Bar;
                struct FooBar;

                impl Bar {
                    fn foo(&self) { unimplemented!() }
                }

                impl FooBar {
                    fn foo(&self) { unimplemented!() }
                      //X
                }

                impl $traitName<f64> for Foo {
                    type Output = Bar;
                    fn $itemName(self, rhs: f64) -> Bar { unimplemented!() }
                }

                impl $traitName<i32> for Foo {
                    type Output = FooBar;
                    fn $itemName(self, rhs: i32) -> FooBar { unimplemented!() }
                }

                fn foo(lhs: Foo, rhs: i32) {
                    let x = lhs $sign rhs;
                    x.foo()
                     //^
                }
            """)
        }
    }

    fun `test generic method with type parameters`() = checkByCode("""
        struct S;
        impl S {
            fn make_t<T>(&self) -> T { unimplemented!() }
        }

        struct X;
        impl X { fn foo(&self) {} }
                   //X
        fn main() {
            let t = S.make_t::<X>();
            t.foo();
        }    //^
    """)

    fun `test trait with bounds on itself`() = checkByCode("""
        trait Foo<T: Foo<T>> {
            fn foo(&self) { }
        }     //X

        impl Foo<()> for () { }

        fn bar<T: Foo<T>>(t: T) {
            t.foo()
        }    //^

        fn main() { bar(()) }
    """)

    fun `test bound associated type`() = checkByCode("""
        trait Tr { type Item; }
                      //X
        struct S<A>(A);
        impl<B: Tr> S<B> { fn foo(self) -> B::Item { unimplemented!() } }
                                            //^
    """)

    fun `test bound associated type in explicit UFCS form`() = checkByCode("""
        trait Tr { type Item; }
                      //X
        struct S<A>(A);
        impl<B: Tr> S<B> { fn foo(self) -> <B as Tr>::Item { unimplemented!() } }
                                                    //^
    """)

    fun `test bound inherited associated type`() = checkByCode("""
        trait Tr1 { type Item; }
                       //X
        trait Tr2: Tr1 {}
        struct S<A>(A);
        impl<B: Tr2> S<B> { fn foo(self) -> B::Item { unimplemented!() } }
                                             //^
    """)

    fun `test no stack overflow on self unification with Eq bound`() = checkByCode("""
        pub trait PartialEq<Rhs: ?Sized> {}
        pub trait Eq: PartialEq<Self> {}
        struct S<A>(A);

        impl<T: Eq> S<T> {
            fn foo(self) {
                self.bar()
            }      //^ unresolved
        }
    """)

    fun `test no stack overflow (issue 1523)`() = checkByCode("""
        struct S1;
        struct S2<A>(A);

        trait Tr { type Item: Sized; }
        impl Tr for S1 { type Item = S2<S1>; }
        impl<B: Tr> S2<B> { fn foo(&self) {} }
                             //X
        fn main() {
            S2(S1).foo()
        }         //^
    """)

    fun `test no stack overflow (issue 1578)`() = checkByCode("""
        pub trait Rem<RHS=Self> {
            type Output = Self;
        }

        struct S<A>(A);
        impl<B: Rem> S<B> { fn foo(&self) {} }
                             //X
        fn foo<C: Rem>(t: C) {
            S(t).foo()
        }      //^
    """)

    fun `test resolve assoc constant`() = checkByCode("""
        trait T {
            const C: i32;
        }       //X

        fn foo<X: T>() {
            X::C
        }    //^
    """)

    fun `test assoc type in fn parameter`() = checkByCode("""
        pub trait Iter {
            type Item;
                //X

            fn scan<St, B, F>(self, initial_state: St, f: F) -> Scan<Self, St, F>
                where Self: Sized, F: FnMut(&mut St, Self::Item) -> Option<B> { unimplemented!() }
        }                                                 //^
    """)

    fun `test direct trait methods wins over inherent via deref`() = checkByCode("""
        struct Foo;
        impl Foo {
            fn foo(&self) { println!("Inherent"); }
        }

        struct Bar(Foo);
        impl ::std::ops::Deref for Bar {
            type Target = Foo;
            fn deref(&self) -> &Foo {
                &self.0
            }
        }

        trait T {
            fn foo(&self) { println!("From a trait"); }
        }     //X
        impl T for Bar {}

        fn main() {
            let bar = Bar(Foo);
            bar.foo();
        }      //^
    """)
}
