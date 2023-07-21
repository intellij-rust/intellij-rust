/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.type

import org.rust.CheckTestmarkHit
import org.rust.lang.core.types.infer.TypeInferenceMarks

class RsGenericExpressionTypeInferenceTest : RsTypificationTestBase() {
    fun `test generic field`() = testExpr("""
        struct S<T> { field: T }

        fn foo(s: S<f64>) {
            let x = s.field;
            x;
          //^ f64
        }
    """)

    fun `test generic field reference`() = testExpr("""
        struct S<'a, T> { field: &'a T }

        fn foo(s: S<'static, f64>) {
            let x = s.field;
            x;
          //^ &f64
        }
    """)

    fun `test nested generic field`() = testExpr("""
        struct A<T> { field: T }
        struct B<S> { field: S }

        fn foo(s: A<B<f64>>) {
            let x = s.field.field;
            x;
          //^ f64
        }
    """)

    fun `test nested generic field 2`() = testExpr("""
        struct S<T> { field: T }

        fn foo(s: S<S<u64>>) {
            let x = s.field.field;
            x;
          //^ u64
        }
    """)

    fun `test generic array`() = testExpr("""
        struct S<T> { field: [T; 1] }

        fn foo(s: S<f64>) {
            let x = s.field;
            x;
          //^ [f64; 1]
        }
    """)

    fun `test generic slice`() = testExpr("""
        struct S<T: 'static> { field: &'static [T] }

        fn foo(s: S<f64>) {
            let x = s.field;
            x;
          //^ &[f64]
        }
    """)

    fun `test generic const ptr`() = testExpr("""
        struct S<T> { field: *const T }

        fn foo(s: S<f64>) {
            let x = s.field;
            x;
          //^ *const f64
        }
    """)

    fun `test generic mut ptr`() = testExpr("""
        struct S<T> { field: *mut T }

        fn foo(s: S<f64>) {
            let x = s.field;
            x;
          //^ *mut f64
        }
    """)

    fun `test generic method`() = testExpr("""
        struct B<T> { field: T }

        impl<T> B<T> {
            fn unwrap(self) -> T { self.field }
        }

        fn foo(s: B<i32>) {
            let x = s.unwrap();
            x;
          //^ i32
        }
    """)

    fun `test two parameters`() = testExpr("""
         enum Result<T, E> { Ok(T), Err(E)}
         impl<T, E> Result<T, E> {
             fn unwrap(self) -> T { unimplemented!() }
         }

         fn foo(r: Result<(u32, u32), ()>) {
             let x = r.unwrap();
             x;
           //^ (u32, u32)
         }
    """)

    fun `test param swap`() = testExpr("""
        struct S<A, B> { a: A, b: B}

        impl<X, Y> S<Y, X> {
            fn swap(self) -> (X, Y) { (self.b, self.a) }
        }

        fn f(s: S<i32, ()>) {
            let x = s.swap();
            x;
          //^ ((), i32)
        }
    """)

    fun `test param repeat`() = testExpr("""
        struct S<A, B> { a: A, b: B}

        impl <T> S<T, T> {
            fn foo(self) -> (T, T) { (self.a, self.b) }
        }

        fn f(s: S<i32, i32>) {
            let x = s.foo();
            x;
          //^ (i32, i32)
        }
    """)

    fun `test partial spec`() = testExpr("""
        struct S<A, B> { a: A, b: B}

        impl <T> S<i32, T> {
            fn foo(self) -> T { self.b }
        }

        fn f(s: S<i32, ()>) {
            let x = s.foo();
            x;
          //^ ()
        }
    """)

    fun `test generic function`() = testExpr("""
        fn f<T>(t: T) -> T { t }

        fn main() {
            let a = f(0i32);
            a;
          //^ i32
        }
    """)

    fun `test generic function 2`() = testExpr("""
        fn f<T2, T1>(t1: T1, t2: T2) -> (T1, T2) { (t1, t2) }

        fn main() {
            let a = f(0u8, 1u16);
            a;
          //^ (u8, u16)
        }
    """)

    fun `test generic function 3`() = testExpr("""
        fn f<T>(t: T) -> T { t }

        fn main() {
            let a = f::<u8>(1);
            a;
          //^ u8
        }
    """)

    fun `test generic function pointer`() = testExpr("""
        fn f<T>(t: T) -> T { t }

        fn main() {
            let f = f::<u8>;
            let r = f(1);
            r;
          //^ u8
        }
    """)

    fun `test generic function pointer 2`() = testExpr("""
        fn f<T1, T2>(t1: T1, t2: T2) -> (T1, T2) { (t1, t2) }

        fn main() {
            let f = f::<u8, _>;
            let r = f(1, 2);
            r;
          //^ (u8, i32)
        }
    """)

    fun `test static method`() = testExpr("""
        struct S<A> { value: A }
        impl<B> S<B> {
            fn new(t: B) -> S<B> { S {value: t} }
        }

        fn main() {
            let a = S::new(0i32);
            a.value;
            //^ i32
        }
    """)

    fun `test recursive type`() = testExpr("""
        struct S<T> {
            rec: S<T>,
            field: T
        }

        fn foo(s: S<char>) {
            let x = s.rec.field;
            x;
          //^ char
        }
    """)

    fun `test Self type`() = testExpr("""
        trait T {
            fn foo(&self) { self; }
                            //^ &Self
        }
    """)

    fun `test self 1`() = testExpr("""
        struct Foo;
        impl Foo {
            fn foo(self) {
                self;
              //^ Foo
            }
        }
    """)

    fun `test self 2`() = testExpr("""
        struct Foo;
        impl Foo {
            fn foo(self: Self) {
                self;
              //^ Foo
            }
        }
    """)

    fun `test self 3`() = testExpr("""
        struct Foo;
        impl Foo {
            fn foo(self: Foo) {
                self;
              //^ Foo
            }
        }
    """)

    fun `test &mut self 1`() = testExpr("""
        struct Foo;
        impl Foo {
            fn foo(&mut self) {
                self;
              //^ &mut Foo
            }
        }
    """)

    fun `test &mut self 2`() = testExpr("""
        struct Foo;
        impl Foo {
            fn foo(self: &mut Self) {
                self;
              //^ &mut Foo
            }
        }
    """)

    fun `test &mut self 3`() = testExpr("""
        struct Foo;
        impl Foo {
            fn foo(self: &mut Foo) {
                self;
              //^ &mut Foo
            }
        }
    """)

    fun `test box self 1`() = testExpr("""
        struct Box<T>(T);
        struct Foo;
        impl Foo {
            fn foo(self: Box<Self>) {
                self;
              //^ Box<Foo>
            }
        }
    """)

    fun `test box self 2`() = testExpr("""
        struct Box<T>(T);
        struct Foo;
        impl Foo {
            fn foo(self: Box<Foo>) {
                self;
              //^ Box<Foo>
            }
        }
    """)

    fun `test struct expr`() = testExpr("""
        struct S<T> { a: T }
        fn main() {
            let x = S { a: 5u16 };
            x.a;
            //^ u16
        }
    """)

    fun `test struct field shorthand`() = testExpr("""
        enum E<T> { A, B(T) }
        struct S<T> { item: E<T> }
        fn foo() -> S<u8> {
            let item = E::A;
                     //^ E<u8>
            S { item }
        }
    """)

    fun `test struct expr with 2 fields of same type 1`() = testExpr("""
        struct X;
        struct S<T> { a: T, b: T }
        fn main() {
            let x = S { a: X, b: unimplemented!() };
            x.b;
            //^ X
        }
    """)

    // TODO fix `!` unification and replace `0` to `unimplemented!()`
    fun `test struct expr with 2 fields of same type 2`() = testExpr("""
        struct S<T> { a: T, b: T }
        fn main() {
            let x = S { a: 0, b: 1u8 };
            x.a;
            //^ u8
        }
    """)

    fun `test struct expr with 2 fields of different types`() = testExpr("""
        struct X; struct Y;
        struct S<T1, T2> { a: T1, b: T2 }
        fn main() {
            let x = S { a: X, b: Y };
            (x.a, x.b);
          //^ (X, Y)
        }
    """)

    fun `test struct expr with explicit type parameter`() = testExpr("""
        struct S<T> {a: T}
        fn main() {
            let x = S::<u8>{a: 1};
            x.a;
            //^ u8
        }
    """)

    fun `test struct expr with explicit and omitted type parameter`() = testExpr("""
        struct S<T1, T2> {a: T1, b: T2}
        fn main() {
            let x = S::<u8, _>{a: 1, b: 2};
            (x.a, x.b);
          //^ (u8, i32)
        }
    """)

    fun `test struct update syntax`() = testExpr("""
        struct S<T> {
            f: T,
        }
        impl<T> S<T> {
            fn new(v: T) -> Self {
                S { f: v }
            }
        }
        fn main() {
            let a = S { ..S::new(1u8) };
            a;
          //^ S<u8>
        }
    """)

    fun `test struct update syntax 2`() = testExpr("""
        struct S<T> {
            f: T,
        }
        impl<T> S<T> {
            fn new() -> Self {
                S { f: 0 }
            }
        }
        fn main() {
            let a = S::<u8> { ..S::new() };
                                    //^ S<u8>
        }
    """)

    fun `test tuple struct expression`() = testExpr("""
        struct S<T> (T);
        fn main() {
            let x = S(5u16);
            x.0;
            //^ u16
        }
    """)

    fun `test tuple struct expr with explicit type parameter`() = testExpr("""
        struct S<T> (T);
        fn main() {
            let x = S::<u8>(1);
            x.0;
            //^ u8
        }
    """)

    fun `test tuple struct expr with explicit and omitted type parameter`() = testExpr("""
        struct S<T1, T2> (T1, T2);
        fn main() {
            let x = S::<u8, _>(1, 2);
            (x.0, x.1);
          //^ (u8, i32)
        }
    """)

    fun `test reference to generic tuple constructor`() = testExpr("""
        struct S<T>(T);
        fn main() {
            let f = S::<u8>;
            f(1).0;
        }      //^ u8
    """)

    fun `test struct new with type parameters`() = testExpr("""
        struct Foo<F>(F);
        impl<T> Foo<T> {
            fn new() -> Self { loop {} }
        }
        fn main() {
            let x = Foo::<u16>::new();
            x;
          //^ Foo<u16>
        }
    """)

    fun `test generic alias`() = testExpr("""
        struct S1<T>(T);
        struct S3<T1, T2, T3>(T1, T2, T3);

        type A<T1, T2> = S3<T2, S1<T1>, S3<S1<T2>, T2, T2>>;
        type B = A<u16, u8>;

        fn f(b: B) {
            (b.0, (b.1).0, ((b.2).0).0);
          //^ (u8, u16, u8)
        }
    """)

    fun `test struct with alias 1`() = testExpr("""
        struct S<T> { a: T }
        type T1 = S<u8>;
        type T2 = T1;
        fn main() {
            T2 { a: 1 };
        } //^ S<u8>
    """)

    fun `test struct with alias 2`() = testExpr("""
        struct S<T> { a: T }
        type T1<U> = S<U>;
        type T2 = T1<u8>;
        fn main() {
            T2 { a: 1 };
        } //^ S<u8>
    """)

    fun `test struct with alias 3`() = testExpr("""
        struct S<T> { a: T }
        type T1<U> = S<U>;
        type T2<V> = T1<V>;
        fn main() {
            T2 { a: 1u8 };
        } //^ S<u8>
    """)

    fun `test enum with alias 1`() = testExpr("""
        enum Foo<T, U> {
            A(T),
            B(U)
        }
        type Baz = Foo<i32, u8>;
        fn main() {
            let a = Baz::A(123);
            a;
        } //^ Foo<i32, u8>
    """)

    fun `test enum with alias 2`() = testExpr("""
        enum Foo<T, U> {
            A(T),
            B(U)
        }
        type Bar<T> = Foo<T, u8>;
        type Baz = Bar<i32>;
        fn main() {
            let a = Baz::A(123);
            a;
        } //^ Foo<i32, u8>
    """)

    fun `test generic struct arg`() = testExpr("""
        struct Foo<F>(F);
        fn foo<T>(xs: Foo<T>) -> T { unimplemented!() }
        fn main() {
            let x = foo(Foo(123));
            x;
          //^ i32
        }
    """)

    fun `test generic enum arg`() = testExpr("""
        enum Foo<F> { V(F) }
        fn foo<T>(xs: Foo<T>) -> T { unimplemented!() }
        fn main() {
            let x = foo(Foo::V(123));
            x;
          //^ i32
        }
    """)

    fun `test generic tuple arg`() = testExpr("""
        fn foo<T, F>(xs: (T, F)) -> F { unimplemented!() }
        fn main() {
            let x = foo((123, "str"));
            x;
          //^ &str
        }
    """)

    fun `test generic reference arg`() = testExpr("""
        fn foo<T>(xs: &T) -> T { unimplemented!() }
        fn main() {
            let x = foo(&8u64);
            x;
          //^ u64
        }
    """)

    fun `test generic pointer arg`() = testExpr("""
        fn foo<T>(xs: *const T) -> T { unimplemented!() }
        fn main() {
            let x = foo(&8u16 as *const u16);
            x;
          //^ u16
        }
    """)

    fun `test generic array arg`() = testExpr("""
        fn foo<T>(xs: [T; 4]) -> T { unimplemented!() }
        fn main() {
            let x = foo([1, 2, 3, 4]);
            x;
          //^ i32
        }
    """)

    fun `test generic slice arg`() = testExpr("""
        fn foo<T>(xs: &[T]) -> T { unimplemented!() }
        fn main() {
            let slice: &[&str] = &["foo", "bar"];
            let x = foo(slice);
            x;
          //^ &str
        }
    """)

    fun `test complex generic arg`() = testExpr("""
        struct Foo<T1, T2>(T1, T2);
        enum Bar<T3, T4> { V(T3, T4) }
        struct FooBar<T5, T6>(T5, T6);

        fn foo<F1, F2, F3, F4>(x: FooBar<Foo<F1, F2>, Bar<F3, F4>>) -> (Bar<F4, F1>, Foo<F3, F2>) { unimplemented!() }
        fn main() {
            let x = foo(FooBar(Foo(123, "foo"), Bar::V([0.0; 3], (0, false))));
            x;
          //^ (Bar<(i32, bool), i32>, Foo<[f64; 3], &str>)
        }
    """)

    fun `test array to slice`() = testExpr("""
        fn foo<T>(xs: &[T]) -> T { unimplemented!() }
        fn main() {
            let x = foo(&[1, 2, 3]);
            x;
          //^ i32
        }
    """)

    fun `test array to slice 2`() = testExpr("""
        fn foo<T>(xs: &[T]) -> T { unimplemented!() }
        fn main() {
            let a = [1, 2, 3];
            let x = foo(&a);
            x;
          //^ i32
        }
    """)

    fun `test generic struct method arg`() = testExpr("""
        struct Foo<F>(F);
        struct Bar<B>(B);
        impl<T1> Foo<T1> {
            fn foo<T2>(&self, bar: Bar<T2>) -> (T1, T2) { unimplemented!() }
        }
        fn main() {
            let x = Foo("foo").foo(Bar(123));
            x;
          //^ (&str, i32)
        }
    """)

    fun `test generic enum method arg`() = testExpr("""
        struct Foo<F>(F);
        enum Bar<B> { V(B) }
        impl<T1> Foo<T1> {
            fn foo<T2>(&self, bar: Bar<T2>) -> (T1, T2) { unimplemented!() }
        }
        fn main() {
            let x = Foo(0.0).foo(Bar::V("bar"));
            x;
          //^ (f64, &str)
        }
    """)

    fun `test generic tuple method arg`() = testExpr("""
        struct Foo<F>(F);
        impl<T1> Foo<T1> {
            fn foo<T2, T3>(&self, xs: (T2, T3)) -> (T1, T2, T3) { unimplemented!() }
        }
        fn main() {
            let x = Foo(123).foo((true, "str"));
            x;
          //^ (i32, bool, &str)
        }
    """)

    fun `test generic reference method arg`() = testExpr("""
        struct Foo<F>(F);
        impl<T1> Foo<T1> {
            fn foo<T2>(&self, xs: &T2) -> (T2, T1) { unimplemented!() }
        }
        fn main() {
            let x = Foo((1, 2)).foo(&8u64);
            x;
          //^ (u64, (i32, i32))
        }
    """)

    fun `test generic pointer method arg`() = testExpr("""
        struct Foo<F>(F);
        impl<T1> Foo<T1> {
            fn foo<T2>(&self, xs: *const T2) -> (T2, T1) { unimplemented!() }
        }
        fn main() {
            let x = Foo("foo").foo(&8u16 as *const u16);
            x;
          //^ (u16, &str)
        }
    """)

    fun `test generic array method arg`() = testExpr("""
        struct Foo<F>(F);
        impl<T1> Foo<T1> {
            fn foo<T2>(&self, xs: [T2; 4]) -> (T2, T1) { unimplemented!() }
        }
        fn main() {
            let x = Foo(0.0).foo([1, 2, 3, 4]);
            x;
          //^ (i32, f64)
        }
    """)

    fun `test generic slice method arg`() = testExpr("""
        struct Foo<F>(F);
        impl<T1> Foo<T1> {
            fn foo<T2>(&self, xs: &[T2]) -> (T2, T1) { unimplemented!() }
        }
        fn main() {
            let slice: &[&str] = &["foo", "bar"];
            let x = Foo(64u8).foo(slice);
            x;
          //^ (&str, u8)
        }
    """)

    fun `test complex generic method arg`() = testExpr("""
        struct Foo<T1, T2>(T1, T2);
        enum Bar<T3, T4> { V(T3, T4) }
        struct FooBar<T5>(T5);

        impl<F1, F2> Foo<F1, F2> {
            fn foo<F3, F4>(&self, x: FooBar<Bar<F3, F4>>) -> (Bar<F4, F1>, Foo<F3, F2>) { unimplemented!() }
        }
        fn main() {
            let x = Foo(123, "foo").foo(FooBar(Bar::V([0.0; 3], (0, false))));
            x;
          //^ (Bar<(i32, bool), i32>, Foo<[f64; 3], &str>)
        }
    """)

    fun `test infer generic argument from trait bound`() = testExpr("""
        struct S<X>(X);
        trait Tr<Y> { fn foo(&self) -> Y; }
        impl<Z> Tr<Z> for S<Z> { fn foo(&self) -> Z { unimplemented!() } }
        fn bar<A, B: Tr<A>>(b: B) -> A { b.foo() }
        fn main() {
            let a = bar(S(1));
            a;
        } //^ i32
    """)

    fun `test infer generic argument from trait bound (aliased impl)`() = testExpr("""
        struct S<X>(X);
        type Alias<X> = S<X>;
        trait Tr<Y> { fn foo(&self) -> Y; }
        impl<Z> Tr<Z> for Alias<Z> { fn foo(&self) -> Z { unimplemented!() } }
        fn bar<A, B: Tr<A>>(b: B) -> A { b.foo() }
        fn main() {
            let a = bar(S(1));
            a;
        } //^ i32
    """)

    fun `test infer complex generic argument from trait bound`() = testExpr("""
        struct S<A>(A);
        trait Tr<B> { fn foo(&self) -> B; }
        impl<C, D> Tr<(C, D)> for S<(C, D)> { fn foo(&self) -> (C, D) { unimplemented!() } }
        fn bar<E, F, G: Tr<(E, F)>>(b: G) -> (E, F) { b.foo() }
        fn main() {
            let a = bar(S((1u8, 1u16)));
            a;
        } //^ (u8, u16)
    """)

    fun `test infer generic argument from trait bound with multiple impls`() = testExpr("""
        struct S<A>(A);
        trait Tr<B> {}
        trait Bound1 {}
        trait Bound2 {}

        impl<C: Bound1> Tr<u8> for S<C> {}
        impl<D: Bound2> Tr<u16> for S<D> {}

        struct X;
        impl Bound2 for X {}

        fn foo<T1, T2>(_: T1) -> T2 where T1: Tr<T2> { unimplemented!() }
        fn main() {
            let a = foo(S(X));
            a;
        } //^ u16
    """)

    fun `test Self substitution to assoc fn in trait impl for primitive`() = testExpr("""
        trait Tr { fn foo() -> Self { unimplemented!() } }
        impl Tr for u64 {}
        fn main () {
            let a = u64::foo();
            a;
        } //^ u64
    """)

    fun `test Self substitution to trait method`() = testExpr("""
        trait Tr<A> { fn wrap(self) -> S<Self> where Self: Sized { unimplemented!() } }
        struct X;
        struct S<C>(C);
        impl<D> Tr<D> for S<D> {}
        fn main() {
            let a = S(X).wrap().wrap().wrap();
            a;
        } //^ S<S<S<S<X>>>>
    """)

    fun `test Self substitution to trait method with deref`() = testExpr("""
        trait Tr<A> { fn wrap(self) -> S<Self> where Self: Sized { unimplemented!() } }
        struct X;
        struct S<C>(C);
        impl<D> Tr<D> for S<D> {}
        fn main() {
            let a = (&S(X)).wrap().wrap().wrap();
            a;
        } //^ S<S<S<S<X>>>>
    """)

    fun `test Self substitution to impl method`() = testExpr("""
        trait Tr<A> { fn wrap(self) -> S<Self> where Self: Sized { unimplemented!() } }
        struct X;
        struct S<C>(C);
        impl<D> Tr<D> for S<D> { fn wrap(self) -> S<Self> where Self: Sized { unimplemented!() } }
        fn main() {
            let a = S(X).wrap().wrap().wrap();
            a;
        } //^ S<S<S<S<X>>>>
    """)

    fun `test recursive receiver substitution`() = testExpr("""
        trait Tr<A> {
            fn wrap(self) -> S<Self> where Self: Sized { unimplemented!() }
            fn fold(self) -> A where Self: Sized { unimplemented!() }
        }

        struct X;
        struct S1<B>(B);
        struct S<C>(C);

        impl<D> Tr<D> for S1<D> {}
        impl<Src, Dst> Tr<Dst> for S<Src> where Src: Tr<Dst> {}

        fn main() {
            let a = S1(X).wrap().wrap().wrap().fold();
            a;
        } //^ X
    """)

    fun `test bound associated type`() = testExpr("""
        trait Tr { type Item; }
        struct S<A>(A);
        impl<B: Tr> S<B> { fn foo(self) -> B::Item { unimplemented!() } }

        struct X;
        impl Tr for X { type Item = u8; }
        fn main() {
            let a = S(X).foo();
            a;
        } //^ u8
    """)

    fun `test bound generic associated type`() = testExpr("""
        trait Tr { type Item<A>; }
        struct S<B>(B);
        impl<C: Tr> S<C> { fn foo(self) -> C::Item<u8> { unimplemented!() } }

        struct X;
        struct Vec<T>(T);
        impl Tr for X { type Item<D> = Vec<D>; }

        fn main() {
            let a = S(X).foo();
            a;
        } //^ Vec<u8>
    """)

    fun `test bound associated type in explicit UFCS form`() = testExpr("""
        trait Tr { type Item; }
        struct S<A>(A);
        impl<B: Tr> S<B> { fn foo(self) -> <B as Tr>::Item { unimplemented!() } }

        struct X;
        impl Tr for X { type Item = u8; }
        fn main() {
            let a = S(X).foo();
            a;
        } //^ u8
    """)

    fun `test bound generic associated type in explicit UFCS form`() = testExpr("""
        trait Tr { type Item<A>; }
        struct S<B>(B);
        impl<C: Tr> S<C> { fn foo(self) -> <C as Tr>::Item<u8> { unimplemented!() } }

        struct X;
        impl Tr for X { type Item<D> = Vec<D>; }
        struct Vec<T>(T);
        fn main() {
            let a = S(X).foo();
            a;
        } //^ Vec<u8>
    """)

    fun `test bound inherited associated type`() = testExpr("""
        trait Tr1 { type Item; }
        trait Tr2: Tr1 {}
        struct S<A>(A);
        impl<B: Tr2> S<B> { fn foo(self) -> B::Item { unimplemented!() } }

        struct X;
        impl Tr1 for X { type Item = u8; }
        impl Tr2 for X {}
        fn main() {
            let a = S(X).foo();
            a;
        } //^ u8
    """)

    fun `test bound inherited generic associated type`() = testExpr("""
        trait Tr1 { type Item<A>; }
        trait Tr2: Tr1 {}
        struct S<B>(B);
        impl<C: Tr2> S<C> { fn foo(self) -> C::Item<u8> { unimplemented!() } }

        struct X;
        struct Vec<T>(T);
        impl Tr1 for X { type Item<D> = Vec<D>; }
        impl Tr2 for X {}
        fn main() {
            let a = S(X).foo();
            a;
        } //^ Vec<u8>
    """)

    fun `test bound inherited associated type in explicit UFCS form`() = testExpr("""
        trait Tr1 { type Item; }
        trait Tr2: Tr1 {}
        struct S<A>(A);
        impl<B: Tr2> S<B> { fn foo(self) -> <B as Tr1>::Item { unimplemented!() } }

        struct X;
        impl Tr1 for X { type Item = u8; }
        impl Tr2 for X {}
        fn main() {
            let a = S(X).foo();
            a;
        } //^ u8
    """)

    fun `test bound inherited generic associated type in explicit UFCS form`() = testExpr("""
        trait Tr1 { type Item<A>; }
        trait Tr2: Tr1 {}
        struct S<B>(B);
        impl<C: Tr2> S<C> { fn foo(self) -> <C as Tr1>::Item<u8> { unimplemented!() } }

        struct X;
        struct Vec<T>(T);
        impl Tr1 for X { type Item<D> = Vec<D>; }
        impl Tr2 for X {}
        fn main() {
            let a = S(X).foo();
            a;
        } //^ Vec<u8>
    """)

    fun `test 2 bound associated types`() = testExpr("""
        trait Tr { type Item; }
        struct S<A, B>(A, B);
        impl<C: Tr, D: Tr> S<C, D> { fn foo(self) -> (C::Item, D::Item) { unimplemented!() } }

        struct X;
        struct Y;
        impl Tr for X { type Item = u8; }
        impl Tr for Y { type Item = u16; }
        fn main() {
            let a = S(X, Y).foo();
            a;
        } //^ (u8, u16)
    """)

    fun `test 2 bound generic associated types`() = testExpr("""
        trait Tr { type Item<A>; }
        struct S<B, C>(B, C);
        impl<D: Tr, E: Tr> S<D, E> { fn foo(self) -> (D::Item<u8>, E::Item<u16>) { unimplemented!() } }

        struct X;
        struct Y;
        struct Vec<T>(T);
        struct Option<T>(T);
        impl Tr for X { type Item<F> = Vec<F>; }
        impl Tr for Y { type Item<G> = Option<G>; }
        fn main() {
            let a = S(X, Y).foo();
            a;
        } //^ (Vec<u8>, Option<u16>)
    """)

    fun `test recursive receiver substitution using associated type`() = testExpr("""
        trait Tr {
            type Item;
            fn wrap(self) -> S<Self> where Self: Sized { unimplemented!() }
            fn fold(self) -> Self::Item where Self: Sized { unimplemented!() }
        }

        struct X;
        struct S1<A>(A);
        struct S<B>(B);

        impl<C> Tr for S1<C> { type Item = C; }
        impl<D: Tr> Tr for S<D> { type Item = D::Item; }

        fn main() {
            let a = S1(X).wrap().wrap().wrap().fold();
            a;
        } //^ X
    """)

    fun `test recursive receiver substitution using generic associated type`() = testExpr("""
        trait Tr {
            type Item<A>;
            fn wrap(self) -> S<Self> where Self: Sized { unimplemented!() }
            fn fold(self) -> Self::Item<Y> where Self: Sized { unimplemented!() }
        }

        struct X; struct Y;
        struct S1<B>(B);
        struct S<C>(C);
        struct Result<A, B>(A, B);

        impl<D> Tr for S1<D> { type Item<E> = Result<D, E>; }
        impl<F: Tr> Tr for S<F> { type Item<G> = F::Item<G>; }

        fn main() {
            let a = S1(X).wrap().wrap().wrap().fold();
            a;
        } //^ Result<X, Y>
    """)

    fun `test recursive receiver substitution using inherited associated type`() = testExpr("""
        trait Tr1 { type Item; }

        trait Tr: Tr1 {
            fn wrap(self) -> S<Self> where Self: Sized { unimplemented!() }
            fn fold(self) -> Self::Item where Self: Sized { unimplemented!() }
        }

        struct X;
        struct S1<A>(A);
        struct S<B>(B);

        impl<C>     Tr1 for S1<C> { type Item = C; }
        impl<D: Tr> Tr1 for S<D>  { type Item = D::Item; }

        impl<E>     Tr for S1<E> {}
        impl<F: Tr> Tr for S<F>  {}

        fn main() {
            let a = S1(X).wrap().wrap().wrap().fold();
            a;
        } //^ X
    """)

    fun `test recursive receiver substitution using inherited generic associated type`() = testExpr("""
        trait Tr1 { type Item<A>; }
        trait Tr: Tr1 {
            fn wrap(self) -> S<Self> where Self: Sized { unimplemented!() }
            fn fold(self) -> Self::Item<Y> where Self: Sized { unimplemented!() }
        }

        struct X; struct Y;
        struct S1<A>(A);
        struct S<B>(B);
        struct Result<A, B>(A, B);

        impl<C>     Tr1 for S1<C> { type Item<D> = Result<C, D>; }
        impl<E: Tr> Tr1 for S<E>  { type Item<F> = E::Item<F>; }

        impl<G>     Tr for S1<G> {}
        impl<H: Tr> Tr for S<H>  {}

        fn main() {
            let a = S1(X).wrap().wrap().wrap().fold();
            a;
        } //^ Result<X, Y>
    """)

    // https://github.com/intellij-rust/intellij-rust/issues/1549
    fun `test Self type in assoc function`() = testExpr("""
        struct S<A>(A);
        impl<B> S<B> {
            fn new(a: B) -> Self { unimplemented!() }
        }
        fn main() {
            let x = S::new(123);
            x;
          //^ S<i32>
        }
    """)

    fun `test Self type in trait assoc function`() = testExpr("""
        struct S<A>(A);
        trait Tr<B> where Self: Sized {
            fn new(a: B) -> Self { unimplemented!() }
        }
        impl<C> Tr<C> for S<C> { }
        fn main() {
            let x = S::new(123);
            x;
          //^ S<i32>
        }
    """)

    fun `test non-inferable associated type`() = testExpr("""
        trait Tr {
            type Item;
            fn bar(&self) -> Self::Item { unimplemented!() }
        }
        fn foo<T: Tr>(t: T) {
            let a = t.bar();
            a;
        } //^ <T as Tr>::Item
    """)

    fun `test non-inferable generic associated type`() = testExpr("""
        trait Tr {
            type Item<A>;
            fn bar<B>(&self, x: B) -> Self::Item<B> { unimplemented!() }
        }
        fn foo<T: Tr>(t: T) {
            let a = t.bar(0);
            a;
        } //^ <T as Tr>::Item<i32>
    """)

    fun `test fn return associated type`() = testExpr("""
        trait Tr { type Item; }
        struct S;
        impl Tr for S {
            type Item = i32;
        }
        fn foo<T: Tr>(t: T) -> T::Item { unimplemented!() }
        fn main() {
            let x = foo(S);
            x;
          //^ i32
        }
    """)

    fun `test fn return generic associated type`() = testExpr("""
        trait Tr { type Item<A>; }
        struct S;
        struct Vec<T>(T);
        impl Tr for S {
            type Item<B> = Vec<B>;
        }
        fn foo<T: Tr, C>(t: T, i: C) -> T::Item<C> { unimplemented!() }
        fn main() {
            let x = foo(S, 0);
            x;
          //^ Vec<i32>
        }
    """)

    fun `test fn return associated type with generic trait bound`() = testExpr("""
        trait SliceIndex<T> { type Output; }
        struct S1; struct S2;
        struct X; struct Y;
        fn get1<I: SliceIndex<S1>>(index: I) -> I::Output { unimplemented!() }
        fn get2<I: SliceIndex<S2>>(index: I) -> <I as SliceIndex<S2>>::Output
            { unimplemented!() }
        impl SliceIndex<S1> for usize { type Output = X; }
        impl SliceIndex<S2> for usize { type Output = Y; }
        fn main() {
            let a = get1(0usize);
            let b = get2(0usize);
            (a, b);
        } //^ (X, Y)
    """)

    fun `test fn return generic associated type with generic trait bound`() = testExpr("""
        trait SliceIndex<T> { type Output<A>; }
        struct S1; struct S2;
        struct X<B>(B); struct Y<B>(B);
        fn get1<I: SliceIndex<S1>, U>(index: I, x: U) -> I::Output<U> { unimplemented!() }
        fn get2<I: SliceIndex<S2>, U>(index: I, x: U) -> <I as SliceIndex<S2>>::Output<U>
        { unimplemented!() }
        impl SliceIndex<S1> for usize { type Output<C> = X<C>; }
        impl SliceIndex<S2> for usize { type Output<C> = Y<C>; }
        fn main() {
            let a = get1(0usize, 1u8);
            let b = get2(0usize, 1u16);
            (a, b);
        } //^ (X<u8>, Y<u16>)
    """)

    fun `test associated type bound`() = testExpr("""
        trait Tr { type Item; }
        trait Tr2<A> {}
        struct S1;
        struct S2;
        impl Tr2<i32> for S1 {}
        impl Tr for S2 { type Item = S1; }
        fn foo<T1, T2>(t: T1) -> T2
            where T1: Tr,
                  T1::Item: Tr2<T2>,
        { unimplemented!() }

        fn main() {
            let x = foo(S2);
            x;
          //^ i32
        }
    """)

    fun `test associated type bound 2`() = testExpr("""
        trait Foo {
            type Item;
            fn foo(&self) -> Self::Item;
        }
        trait Bar<A> {}
        fn bar<A: Bar<B>, B>(t: A) -> B { unimplemented!() }
        fn baz<C, D>(t: C)
            where C: Foo,
                  C::Item: Bar<D> {
            let a = bar(t.foo());
            a;
        } //^ D
    """)

    fun `test nested projections`() = testExpr("""
        trait Trait1 {
            type Item1;
            fn foo(&self) -> Self::Item1 { unimplemented!() }
        }
        trait Trait2 { type Item2; }

        struct X<T>(T);
        struct Y;
        struct Z;

        impl<T> Trait1 for X<T>
            where T: Trait2,
                  T::Item2: Trait1
        {
            type Item1 = <<T as Trait2>::Item2 as Trait1>::Item1;
        }
        impl Trait2 for Y { type Item2 = Z; }
        impl Trait1 for Z { type Item1 = i32; }

        fn main() {
            let a = X(Y).foo();
            a;
        } //^ i32
    """)

    fun `test simple unification`() = testExpr("""
        struct S<T>(T);

        fn main() {
            let a: S<_> = S(0i32);
            a;
        } //^ S<i32>
    """)

    fun `test simple unification 1`() = testExpr("""
        fn main() {
            let a: _ = 0i32;
            a;
        } //^ i32
    """)

    fun `test unify reference`() = testExpr("""
        fn foo<A>(a: A, b: A) {}
        fn main() {
            let a = 0;
                  //^ u8
            let b = 1u8;
            foo(&a, &b);
        }
    """)

    fun `test unify array`() = testExpr("""
        fn foo<A>(a: A, b: A) {}
        fn main() {
            let a = [1, 2];
                      //^ u8
            let b = [1, 2u8];
            foo(a, b);
        }
    """)

    fun `test unify struct`() = testExpr("""
        fn foo<A>(a: A, b: A) {}
        struct S<B> {f: B}
        fn main() {
            let a = 0;
                  //^ u8
            let b = 1u8;
            foo(S { f: a}, S { f: b });
        }
    """)

    fun `test unify struct 2`() = testExpr("""
        struct S<A, B> {a: A, b: B}
        fn main() {
            let a: S<u8, _> = S::<_, u16> {a: 0, b: 1};
            a;
        } //^ S<u8, u16>
    """)

    fun `test unify struct fields`() = testExpr("""
        struct S<A> {a: A, b: A}
        fn main() {
            S {a: 0, b: 1u8};
        }       //^ u8
    """)

    fun `test unify tuple`() = testExpr("""
        fn foo<A>(a: A, b: A) {}
        struct S<B> {f: B}
        fn main() {
            let a = 0;
                  //^ u8
            let b = 1u8;
            foo((a, ), (b, ));
        }
    """)

    fun `test unify fn with polymorphic return type`() = testExpr("""
        pub trait FromIterator<A>: Sized { }
        struct S<B>(B);
        impl<C> FromIterator<C> for S<C> {}

        fn collect<D: FromIterator<i32>>() -> D { unimplemented!() }
        fn foo<E>(a: S<E>) {}

        fn main() {
            let a: S<_> = collect();
            let b = collect::<S<_>>();
            let c = collect();
            foo(c);
            (a, b, c);
        } //^ (S<i32>, S<i32>, S<i32>)
    """)

    fun `test unify method with polymorphic return type`() = testExpr("""
        pub trait FromIterator<A>: Sized { }
        struct S<B>(B);
        impl<C> FromIterator<C> for S<C> {}

        struct Iter<D>(D);
        impl<E> Iter<E> {
            fn collect<F: FromIterator<E>>(&self) -> F { unimplemented!() }
        }
        fn foo<E>(a: S<E>) {}

        struct X;
        fn bar(it: Iter<X>) {
            let a: S<_> = it.collect();
            let b = it.collect::<S<_>>();
            let c = it.collect();
            foo(c);
            (a, b, c);
        } //^ (S<X>, S<X>, S<X>)
    """)

    fun `test unification continued on type mismatch`() = testExpr("""
        struct S<A, B>(A, B);
        fn foo<T>(a: T, b: T) {}
        struct X; struct Y;
        fn main() {
            let a = 0;
            foo(S(X, a), S(Y, 1u8));
            a;
        } //^ u8
    """, allowErrors = true)

    fun `test infer static method arg with multiple impls of the same trait`() = testExpr("""
        pub trait From<T> { fn from(_: T) -> Self; }
        struct S; struct S1;
        impl From<S1> for S { fn from(_: S1) -> S { unimplemented!() } }
        impl From<u8> for S { fn from(_: u8) -> S { unimplemented!() } }
        fn main() {
            let _: S = From::from(0);
        }                       //^ u8
    """)

    @CheckTestmarkHit(TypeInferenceMarks.MethodPickCollapseTraits::class)
    fun `test infer method arg with multiple impls of the same trait`() = testExpr("""
        pub trait Tr<T> { fn foo(&self, _: T); }
        struct S; struct S1;
        impl Tr<S1> for S { fn foo(&self, _: S1) {} }
        impl Tr<u8> for S { fn foo(&self, _: u8) {} }
        fn main() {
            S.foo(0)
        }       //^ u8
    """)

    fun `test infer method arg with multiple impls of the same trait UFCS`() = testExpr("""
        pub trait Tr<T> { fn foo(&self, _: T); }
        struct S; struct S1;
        impl Tr<S1> for S { fn foo(&self, _: S1) {} }
        impl Tr<u8> for S { fn foo(&self, _: u8) {} }
        fn main() {
            let a = S;
            Tr::foo(&a, 0);
        }             //^ u8
    """)

    @CheckTestmarkHit(TypeInferenceMarks.MethodPickCollapseTraits::class)
    fun `test infer method arg with multiple impls of the same trait on multiple deref levels`() = testExpr("""
        #[lang = "deref"]
        trait Deref { type Target; }

        struct A;
        struct B;

        impl Deref for A { type Target = B; }

        trait Tr<T1, T2> { fn foo(&self, t: T1) -> T2 { unimplemented!() } }

        impl Tr<u8, i8> for A {}
        impl Tr<u16, i16> for A {}
        impl Tr<u32, i32> for B {}

        fn main() {
            let a = A.foo(0u16);
            a;
        } //^ i16
    """)

    fun `test infer type by reference coercion`() = testExpr("""
        #[lang = "deref"]
        trait Deref { type Target; }

        struct S<T>(T);
        impl<T> Deref for S<T> { type Target = T;}

        fn main() {
            let _: &u8 = &S(0);
        }                 //^ u8
    """)

    fun `test generic trait object method`() = testExpr("""
        trait Tr<A> { fn foo(&self) -> A { unimplemented!() } }
        fn bar(a: &Tr<u8>) {
            let b = a.foo();
            b;
        } //^ u8
    """)

    fun `test generic trait object bound`() = testExpr("""
        trait Tr<A> {}
        fn foo<B, C>(_: &B) -> C where B: Tr<C> + ?Sized { unimplemented!() }
        fn bar(a: &Tr<u8>) {
            let b = foo(a);
            b;
        } //^ u8
    """)

    fun `test inherited generic trait object method`() = testExpr("""
        trait Tr1<A> { fn foo(&self) -> A { unimplemented!() } }
        trait Tr2<B>: Tr1<B> {}
        fn bar(a: &Tr2<u8>) {
            let b = a.foo();
            b;
        } //^ u8
    """)

    fun `test inherited generic trait object bound`() = testExpr("""
        trait Tr1<A> {}
        trait Tr2<D>: Tr1<D> {}
        fn foo<B, C>(_: &B) -> C where B: Tr1<C> + ?Sized { unimplemented!() }
        fn bar(a: &Tr2<u8>) {
            let b = foo(a);
            b;
        } //^ u8
    """)

    fun `test invalid inherited generic trait object bound with Self`() = testExpr("""
        trait Tr1<A> {}
        trait Tr2: Tr1<Self> {}
        fn foo<B, C>(_: &B) -> C where B: Tr1<C> + ?Sized { unimplemented!() }
        fn bar(a: &dyn Tr2) { // error[E0038]: the trait `Tr2` cannot be made into an object
            let b = foo(a);
            b;
        } //^ <unknown>
    """)

    fun `test generic 'impl Trait' method`() = testExpr("""
        trait Tr<A> { fn foo(&self) -> A { unimplemented!() } }
        fn new() -> impl Tr<u8> { unimplemented!() }
        fn main() {
            let a = new().foo();
            a;
        } //^ u8
    """)

    fun `test generic 'impl Trait' bound`() = testExpr("""
        trait Tr<A> {}
        fn new() -> impl Tr<u8> { unimplemented!() }
        fn foo<B, C>(_: &B) -> C where B: Tr<C> + ?Sized { unimplemented!() }
        fn main() {
            let a = foo(&new());
            a;
        } //^ u8
    """)

    fun `test inherited generic 'impl Trait' method`() = testExpr("""
        trait Tr1<A> { fn foo(&self) -> A { unimplemented!() } }
        trait Tr2<B>: Tr1<B> {}
        fn new() -> impl Tr2<u8> { unimplemented!() }
        fn main() {
            let a = new().foo();
            a;
        } //^ u8
    """)

    fun `test inherited generic 'impl Trait' bound`() = testExpr("""
        trait Tr1<A> {}
        trait Tr2<D>: Tr1<D> {}
        fn new() -> impl Tr2<u8> { unimplemented!() }
        fn foo<B, C>(_: B) -> C where B: Tr1<C> { unimplemented!() }
        fn main() {
            let a = foo(new());
            a;
        } //^ u8
    """)

    fun `test inherited generic 'impl Trait' bound with Self`() = testExpr("""
        trait Tr1<A> {}
        trait Tr2: Tr1<Self> + Sized {}
        fn new() -> impl Tr2 { unimplemented!() }
        fn foo<B, C>(_: B) -> C where B: Tr1<C> { unimplemented!() }
        fn main() {
            let a = foo(new());
            a;
        } //^ impl Tr2
    """)

    fun `test inherited generic type parameter method`() = testExpr("""
        trait Tr1<A> { fn foo(&self) -> A { unimplemented!() } }
        trait Tr2<B>: Tr1<B> {}
        fn bar<T: Tr2<u8>>(t: T) {
            let a = t.foo();
            a;
        } //^ u8
    """)

    fun `test inherited generic type parameter method with bound for reference type`() = testExpr("""
        trait Tr1<A> { fn foo(&self) -> A { unimplemented!() } }
        trait Tr2<B>: Tr1<B> {}
        fn bar<'a, T>(t: &'a T) where &'a T: Tr2<u8> {
            let a = t.foo();
            a;
        } //^ u8
    """)

    fun `test inherited generic type parameter method with bound for reference type 2`() = testExpr("""
        type Alias<'a, T> = &'a T;

        trait Tr1<A> { fn foo(&self) -> A { unimplemented!() } }
        trait Tr2<B>: Tr1<B> {}
        fn bar<'a, T>(t: Alias<'a, T>) where &'a T: Tr2<u8> {
            let a = t.foo();
            a;
        } //^ u8
    """)

    fun `test inherited generic type parameter bound`() = testExpr("""
        trait Tr1<A> {}
        trait Tr2<D>: Tr1<D> {}
        fn foo<B, C>(_: B) -> C where B: Tr1<C> { unimplemented!() }
        fn bar<T: Tr2<u8>>(t: T) {
            let a = foo(t);
            a;
        } //^ u8
    """)

    fun `test reference coercion inside tuple struct init (or fn call)`() = testExpr("""
        #[lang = "deref"]
        trait Deref { type Target; }

        struct Wrapper<T>(T);
        struct RefWrapper<'a, T : 'a>(&'a T);
        impl<T> Deref for Wrapper<T> { type Target = T; }

        fn main() {
            let _: RefWrapper<u8> = RefWrapper(&Wrapper(0));
        }                                             //^ u8
    """)

    fun `test reference coercion inside struct init`() = testExpr("""
        #[lang = "deref"]
        trait Deref { type Target; }

        struct Wrapper<T> { f: T }
        struct RefWrapper<'a, T : 'a> { f: &'a T };
        impl<T> Deref for Wrapper<T> { type Target = T; }

        fn main() {
            let _: RefWrapper<u8> = RefWrapper { f: &Wrapper { f: 0 } };
        }                                                       //^ u8
    """)

    fun `test reference coercion inside method call`() = testExpr("""
        #[lang = "deref"]
        trait Deref { type Target; }

        struct Wrapper<T>(T);
        struct RefWrapper<'a, T : 'a>(&'a T);
        impl<T> Deref for Wrapper<T> { type Target = T; }

        struct A;
        impl A {
            fn ref_wrapper<'a, T : 'a>(&self, t: &'a T) -> RefWrapper<'a, T> {
                RefWrapper(t)
            }
        }

        fn main() {
            let _: RefWrapper<u8> = A.ref_wrapper(&Wrapper(0));
        }                                                //^ u8
    """)

    fun `test type variable resolved for let pattern matching`() = testExpr("""
        struct X;
        fn foo<T>(_: T) -> T { unimplemented!() }
        fn main () {
            let (a,) = foo((X,));
            a;
        } //^ X
    """)

    fun `test type variable resolved for pattern matching`() = testExpr("""
        struct X;
        fn foo<T>(_: T) -> T { unimplemented!() }
        fn main () {
            match foo((X,)) {
                (a,) => { a; }
                _ => {} //^ X
            }
        }
    """)

    fun `test type variable resolved for field lookup`() = testExpr("""
        struct S { field: i32 }
        fn unify<T>(_: T, _: T) {}
        fn foo<T>() -> T { unimplemented!() }

        fn main() {
            let a = foo();
            unify(a, S { field: 0 });
            a.field;
        }   //^ i32
    """)

    fun `test associated type binding in trait bound`() = testExpr("""
        trait Tr { type Item; }

        fn foo<B: Tr<Item=u8>>(_: B) {
            let a: B::Item = 0;
            a;
        } //^ u8
    """)

    fun `test generic associated type binding in trait bound 1`() = testExpr("""
        trait Tr { type Item<A>; }

        fn foo<B: Tr<Item<u8>=u16>>(_: B) {
            let a: B::Item<u8> = 0;
            a;
        } //^ u16
    """)

    fun `test generic associated type binding in trait bound 2`() = expect<Throwable> {
        testExpr("""
            trait Tr { type Item<A>; }

            fn foo<B: Tr<Item<i8>=u16>>(_: B) {
                let a: B::Item<u8> = 0;
                a
            } //^ <unknown>
        """)
    }

    fun `test associated type binding in trait object`() = testExpr("""
        trait Tr {
            type Item;
            fn foo(&self) -> Self::Item;
        }

        fn foo(a: &Tr<Item=u8>) {
            let b = a.foo();
            b;
        } //^ u8
    """)

    fun `test generic associated type binding in trait object 1`() = testExpr("""
        trait Tr {
            type Item<A>;
            fn foo(&self) -> Self::Item<u8>;
        }

        fn foo(a: &Tr<Item<u8>=u16>) {
            let b = a.foo();
            b;
        } //^ u16
    """)

    fun `test generic associated type binding in trait object 2`() = expect<Throwable> {
        testExpr("""
            trait Tr {
                type Item<A>;
                fn foo(&self) -> Self::Item<u8>;
            }

            fn foo(a: &Tr<Item<i8>=u16>) {
                let b = a.foo();
                b;
            } //^ <unknown>
        """)
    }
    fun `test aliased associated type binding in trait object`() = testExpr("""
        trait Tr {
            type Item;
            fn foo(&self) -> Self::Item;
        }

        type TrAlias<T> = Tr<Item=T>;

        fn foo(a: &TrAlias<u8>) {
            let b = a.foo();
            b;
        } //^ u8
    """)

    fun `test aliased generic associated type binding in trait object 1`() = testExpr("""
        trait Tr {
            type Item<A>;
            fn foo(&self) -> Self::Item<u8>;
        }

        type TrAlias<T> = Tr<Item<u8>=T>;

        fn foo(a: &TrAlias<u8>) {
            let b = a.foo();
            b;
        } //^ u8
    """)

    fun `test aliased generic associated type binding in trait object 2`() = expect<Throwable> {
        testExpr("""
            trait Tr {
                type Item<A>;
                fn foo(&self) -> Self::Item<u8>;
            }

            type TrAlias<T> = Tr<Item<i8>=T>;

            fn foo(a: &TrAlias<u8>) {
                let b = a.foo();
                b;
            } //^ <unknown>
        """)
    }

    fun `test associated type binding in 'impl Trait'`() = testExpr("""
        trait Tr {
            type Item;
            fn foo(&self) -> Self::Item;
        }

        fn new() -> impl Tr<Item=u8> { unimplemented!() }
        fn main() {
            let b = new().foo();
            b;
        } //^ u8
    """)

    fun `test infer associated type binding from supertrait`() = testExpr("""
        trait Parent {
            type Item;
            fn item(&self) -> Self::Item;
        }
        trait Child : Parent {}

        struct Foo;
        struct Bar;
        impl Parent for Foo {
            type Item = Bar;
            fn item(&self) -> Self::Item { unimplemented!() }
        }
        impl Child for Foo {}

        fn new() -> impl Child<Item = Bar> { unimplemented!() }
        fn main() {
            let bar = new().item();
            bar;
        } //^ Bar
    """)

    fun `test generic associated type binding in 'impl Trait' 1`() = testExpr("""
        trait Tr {
            type Item<A>;
            fn foo(&self) -> Self::Item<u8>;
        }

        fn new() -> impl Tr<Item<u8>=u16> { unimplemented!() }
        fn main() {
            let b = new().foo();
            b;
        } //^ u16
    """)

    fun `test generic associated type binding in 'impl Trait' 2`() = expect<Throwable> {
        testExpr("""
            trait Tr {
                type Item<A>;
                fn foo(&self) -> Self::Item<u8>;
            }

            fn new() -> impl Tr<Item<i8>=u16> { unimplemented!() }
            fn main() {
                let b = new().foo();
                b;
            } //^ <unknown>
        """)
    }

    fun `test select trait from unconstrained integer`() = testExpr("""
        struct X;
        trait Tr<A> {}
        impl Tr<X> for u8 {}
        fn foo<B: Tr<C>, C>(_: B) -> C { unimplemented!() }
        fn main() {
            let a = foo(0);
            a;
        } //^ X
    """)

    fun `test select projection from unconstrained integer`() = testExpr("""
        struct X;
        trait Tr { type Item; }
        impl Tr for u8 { type Item = X; }
        fn foo<B: Tr>(_: B) -> B::Item { unimplemented!() }
        fn main() {
            let a = foo(0);
            a;
        } //^ X
    """)

    fun `test field type substitution after deref`() = testExpr("""
        #[lang = "deref"]
        trait Deref { type Target; }

        struct S1<A> { field1: A }
        struct S2<B> { parent: S1<B> }
        impl<T> Deref for S2<T> { type Target = S1<T>; }

        fn main() {
            let s1 = S1 { field1: 1u8 };
            let s2 = S2 { parent: s1 };

            let a = s2.field1;
            a;
        } //^ u8
    """)

    fun `test Self substitution inside traits`() = testExpr("""
        struct S<T>(T);
        impl<T> Tr for S<T> { type Item = T; }
        trait Tr: Sized {
            type Item;
            fn wrap<T>(mut self) -> S<Self> { unimplemented!() }
            fn unwrap(self) -> Self::Item { unimplemented!() }

            fn bar(&self) {
                self.wrap().unwrap()
            }               //^ Self
        }
    """)

    fun `test type arguments remap on collapse to trait`() = testExpr("""
        struct S;
        trait Tr<A> { fn foo<B>(a: A, b: B) -> Self; }
        impl Tr<u8> for S { fn foo<C>(a: u8, b: C) -> Self { unimplemented!() } }
        impl Tr<u16> for S { fn foo<D>(a: u16, b: D) -> Self { unimplemented!() } }

        fn main() {
            let a = 0;
            let s = S::foo::<u64>(0u8, a);
            (a, s);
        } //^ (u64, S)
    """)

    fun `test trait bound for reference type`() = testExpr("""
        struct S;
        trait Foo<T> {}
        impl Foo<i32> for &S {}
        fn foo<'a, T1, T2>(t: &'a T1) -> T2 where &'a T1: Foo<T2> { unimplemented!() }
        fn main() {
            let a = foo(&S);
            a;
        } //^ i32
    """)

    fun `test type inferred after unconstrained integer fallback to i32`() = testExpr("""
        pub trait MyAdd<RHS=Self> {
            type Output;
            fn my_add(self, rhs: RHS) -> Self::Output;
        }
        impl MyAdd for i32 {
            type Output = i32;
            fn my_add(self, other: i32) -> i32 { self + other }
        }
        impl MyAdd for u8 {
            type Output = u8;
            fn my_add(self, other: u8) -> u8 { self + other }
        }
        fn main() {
            let a = 0.my_add(0);
            a;
        } //^ i32
    """)

    fun `test type coercion in generic associates function args`() = testExpr("""
        #[lang = "deref"]
        trait Deref { type Target; }

        struct Foo;
        struct Bar;

        impl Deref for Foo {
            type Target = Bar;
        }

        struct Box<T>(T);
        impl<T> Box<T> {
            pub fn new(x: T) -> Box<T> { unimplemented!() }
        }
        fn main() {
            let _: Box<[&Bar; 1]> = (Box::new([&Foo]));
        }                         //^ Box<[&Bar; 1]>
    """)

    fun `test associated constant with complex type`() = testExpr("""
        trait Tr<A> { const C: A; }
        struct S<T>(T);
        impl<T: Tr<T>> Tr<T> for S<T> {
            const C: T = T::C;
        }
        struct X;
        impl Tr<X> for X {
            const C: X = X;
        }
        fn main() {
            let a = S::<X>::C;
            a;
        } //^ X
    """)

    fun `test select trait with use alias`() = testExpr("""
        #[lang = "deref"]
        trait Deref { type Target; }
        mod foo { pub use super::Deref as DerefAlias; }
        struct A; struct B;
        impl foo::DerefAlias for A { type Target = B; }
        fn main() {
            let b = *A;
            b;
        } //^ B
    """)

    // Issue https://github.com/intellij-rust/intellij-rust/issues/4026
    fun `test struct field with associated type`() = testExpr("""
        struct Foo<V: Trait> {
            input: <V as Trait>::Item,
        }
        trait Trait { type Item; }
        struct S; struct X;
        impl Trait for S {
            type Item = X;
        }
        fn main() {
            let foo1 = Foo::<S> {
                input: X,
            };

            foo1.input;
        }      //^ X
    """)

    fun `test struct field with generic associated type`() = testExpr("""
        struct Foo<V: Trait, U, const N: usize> {
            input: <V as Trait>::Item<U, N>,
        }
        trait Trait { type Item<U, const N: usize>; }
        struct S; struct X<U, const N: usize>;
        impl Trait for S {
            type Item<U, const N: usize> = X<U, N>;
        }
        fn main() {
            let foo1 = Foo::<S, u8, 0> {
                input: X,
            };

            foo1.input;
        }      //^ X<u8, 0>
    """)

    // Issue https://github.com/intellij-rust/intellij-rust/issues/4026
    fun `test tuple struct field with associated type`() = testExpr("""
        struct Foo<V: Trait>(<V as Trait>::Item);
        trait Trait { type Item; }
        struct S; struct X;
        impl Trait for S {
            type Item = X;
        }
        fn main() {
            let foo1 = Foo::<S>(X);

            foo1.0;
        }      //^ X
    """)

    fun `test tuple struct field with generic associated type`() = testExpr("""
        struct Foo<V: Trait, U, const N: usize>(<V as Trait>::Item<U, N>);
        trait Trait { type Item<U, const N: usize>; }
        struct S; struct X<U, const N: usize>;
        impl Trait for S {
            type Item<U, const N: usize> = X<U, N>;
        }
        fn main() {
            let foo1 = Foo::<S, u8, 0>(X);

            foo1.0;
        }      //^ X<u8, 0>
    """)

    // Issue https://github.com/intellij-rust/intellij-rust/issues/3999
    fun `test default type argument is not used in expression context 1`() = testExpr("""
        struct S<T = X>(T);
        struct X;
        fn main() {
            let a = S(1);
            a;
        } //^ S<i32>
    """)

    fun `test default const argument is not used in expression context 1`() = testExpr("""
        struct S<const N: usize = 1>;
        fn main() {
            let a = S;
            a;
        } //^ S<<unknown>>
    """)

    fun `test default type argument is not used in expression context 2`() = testExpr("""
        struct S<T = X>(T);
        struct X;
        impl<T> S<T> {
            fn new(t: T) -> S<T> { S(t) }
        }
        fn main() {
            let a = S::new(1);
            a;
        } //^ S<i32>
    """)

    fun `test default const argument is not used in expression context 2`() = testExpr("""
        struct S<const N: usize = 1>;
        impl<const N: usize> S<N> {
            fn new() -> S<N> { S }
        }

        fn main() {
            let a = S::new();
            a;
        } //^ S<<unknown>>
    """)

    fun `test default type argument is used in expression context`() = testExpr("""
        struct S<A, B=()>(A, B);
        impl<T> S<T, ()> {
            fn new() -> Self { unimplemented!() }
        }
        fn main() {
            let s = S::<i32>::new();
            s;
        } //^ S<i32, ()>
    """)

    fun `test default const argument is used in expression context`() = testExpr("""
        struct S<const N: usize, const M: usize = 1>;
        impl<const N: usize> S<N, 1> {
            fn id(self) -> Self { unimplemented!() }
        }
        fn main() {
            let s = S::<0>.id();
            s;
        } //^ S<0, 1>
    """)

    fun `test default type argument is not used in pat context`() = testExpr("""
        struct S<T = X>(T);
        struct X;
        fn main() {
            let S(a) = S(1);
            a;
        } //^ i32
    """)

    fun `test default const argument is not used in pat context`() = testExpr("""
        struct S<const N: usize = 1>([i32; N]);
        fn main() {
            let S(a) = S([1, 2, 3]);
            a;
        } //^ [i32; 3]
    """)

    fun `test default type argument is used in type context 1`() = testExpr("""
        struct S<T = X>(T);
        struct X;
        fn foo(s: S) {
            s;
        } //^ S<X>
    """)

    fun `test default const argument is used in type context 1`() = testExpr("""
        struct S<const N: usize = 1>;
        fn foo(s: S) {
            s;
        } //^ S<1>
    """)

    fun `test default type argument is used in type context 2`() = testExpr("""
        struct S<T1 = X, T2 = Y>(T1, T2);
        struct X; struct Y;
        fn foo(s: S<u8>) {
            s;
        } //^ S<u8, Y>
    """)

    fun `test default const argument is used in type context 2`() = testExpr("""
        struct S<const N1: usize = 1, const N2: usize = 2>;
        fn foo(s: S<0>) {
            s;
        } //^ S<0, 2>
    """)

    fun `test type argument is unknown if not passed`() = testExpr("""
        struct S<T1, T2>(T1, T2);
        fn foo(s: S<u8>) {
            s;
        } //^ S<u8, <unknown>>
    """)

    fun `test UFCS explicit trait type parameter`() = testExpr("""
        struct S;
        trait Foo<T> { fn foo(_: Self) -> T; }
        impl Foo<i32> for S { fn foo(_: Self) -> i32 { unimplemented!() } }
        impl Foo<u32> for S { fn foo(_: Self) -> u32 { unimplemented!() } }
        fn main() {
            let a = Foo::<i32>::foo(S);
            let b = Foo::<u32>::foo(S);
            (a, b);
        } //^ (i32, u32)
    """)

    fun `test trait bounds normalization`() = testExpr("""
        struct X;
        trait Foo<T> {  }
        fn foo<A: Foo<B>, B>(_: A) -> B { unimplemented!() }
        trait Bar { type Item; }
        fn bar<A: Bar<Item=B>, B>(b: B) where A::Item: Foo<X> {
            let a = foo(b);
            a;
        } //^ X
    """)

    fun `test assoc type bound selection 1`() = testExpr("""
        struct X;
        trait Foo<T> {}
        fn foo<A: Foo<B>, B>(_: A) -> B { unimplemented!() }
        trait Bar where Self::Item: Foo<X> { type Item; }
        fn bar<T: Bar>(_: T, b: T::Item) {
            let a = foo(b);
            a;
        } //^ X
    """)

    fun `test assoc type bound selection 2`() = testExpr("""
        struct X;
        trait Foo<T> {}
        fn foo<A: Foo<B>, B>(_: A) -> B { unimplemented!() }
        trait Bar<T> where Self::Item: Foo<T> { type Item; }
        fn bar<T: Bar<X>>(_: T, b: T::Item) {
            let a = foo(b);
            a;
        } //^ X
    """)

    fun `test assoc type bound selection 3`() = testExpr("""
        struct X;
        trait Foo<T> { fn foo(&self) -> T; }
        trait Bar<T> where Self::Item: Foo<T> { type Item; }
        fn bar<T: Bar<X>>(_: T, b: T::Item) {
            let a = b.foo();
            let b = Foo::foo(&b);
            (a, b);
        } //^ (X, X)
    """)

    fun `test assoc type bound selection 4`() = testExpr("""
        struct X;
        trait Foo<T> {}
        fn foo<A: Foo<B>, B>(_: A) -> B { unimplemented!() }
        trait Bar { type Item: Foo<X>; }
        fn bar<T: Bar>(_: T, b: T::Item) {
            let a = foo(b);
            a;
        } //^ X
    """)

    fun `test assoc type bound selection 5`() = testExpr("""
        struct X;
        trait Foo { type Item: Bar; }
        trait Bar: Baz<X> {}
        trait Baz<T> {}
        fn baz<A: Baz<B>, B>(t: A) -> B { unimplemented!() }

        fn foobar<T: Foo>(a: T::Item) {
            let b = baz(a);
            b;
        } //^ X
    """)

    fun `test assoc type bound selection 6`() = testExpr("""
        struct X;
        trait Foo { type Item: Bar1 + Bar2; }
        trait Bar1: Baz<X> {}
        trait Bar2: Baz<X> {}
        trait Baz<T> {}
        fn baz<A: Baz<B>, B>(t: A) -> B { unimplemented!() }

        fn foobar<T: Foo>(a: T::Item) {
            let b = baz(a);
            b;
        } //^ X
    """)

    fun `test assoc type bound selection 7`() = testExpr("""
        struct X;
        trait Foo { type Item: Bar; }
        trait Bar: Baz<Self> {}
        trait Baz<T> {}
        fn baz<A: Baz<B>, B>(t: A) -> B { unimplemented!() }

        fn foobar<T: Foo>(a: T::Item) {
            let b = baz(a);
            b;
        } //^ T
    """)

    fun `test assoc type bound in path selection`() = testExpr("""
        struct X;
        trait Foo<T> {}
        fn foo<A: Foo<B>, B>(_: A) -> B { unimplemented!() }
        trait Bar { type Item; }
        fn bar<T: Bar<Item: Foo<X>>>(_: T, b: T::Item) {
            let a = foo(b);
            a;
        } //^ X
    """)

    fun `test assoc type bound in nested path selection`() = testExpr("""
        struct X;
        trait Foo<T> {}
        fn foo<A: Foo<B>, B>(_: A) -> B { unimplemented!() }
        trait Bar { type Item; }
        fn bar<T: Bar<Item: Bar<Item: Foo<X>>>>(_: T, b: <T::Item as Bar>::Item) {
            let a = foo(b);
            a;
        } //^ X
    """)

    fun `test deref method call with trait bounds`() = testExpr("""
        #[lang = "deref"]
        trait Deref { type Target; }

        struct Lazy<T, F, A, B>(T, F, A, B);
        struct Foo;
        trait Bound<T, A, B> {}
        trait Bound2<T> {}
        impl<A, B> Bound<S<A, B>, A, B> for Foo {}

        impl<T, A, B, F> Lazy<T, F, A, B> {
            fn new(_: F) -> Lazy<T, F, A, B> { todo!() }
        }

        impl<T, A, B, F> Deref for Lazy<T, F, A, B>
            where
                F: Bound<T, A, B>,
                A: Bound2<B>
        {
            type Target = T;
        }

        struct S<A, B>(A, B);
        impl<A, B> S<A, B> {
            fn bar(&self) -> (A, B) { todo!() }
        }
        struct Bar;
        struct Baz;
        impl Bound2<Baz> for Bar {}
        fn foo() {
            let l = Lazy::new(Foo);
            let (a, b) = l.bar();
            let _: Bar = a;
            b;
        } //^ Baz
    """)

    fun `test deref field with trait bounds`() = testExpr("""
        #[lang = "deref"]
        trait Deref { type Target; }

        struct Lazy<T, F, A, B>(T, F, A, B);
        struct Foo;
        trait Bound<T, A, B> {}
        trait Bound2<T> {}
        impl<A, B> Bound<S<A, B>, A, B> for Foo {}

        impl<T, A, B, F> Lazy<T, F, A, B> {
            fn new(_: F) -> Lazy<T, F, A, B> { todo!() }
        }

        impl<T, A, B, F> Deref for Lazy<T, F, A, B>
            where
                F: Bound<T, A, B>,
                A: Bound2<B>
        {
            type Target = T;
        }

        struct S<A, B> { i: (A, B) }
        struct Bar;
        struct Baz;
        impl Bound2<Baz> for Bar {}
        fn foo() {
            let l = Lazy::new(Foo);
            let a = &l.i.0;
            let b = &l.i.1;
            let _: &Bar = a;
            b;
        } //^ &Baz
    """)

    fun `test deref tuple field with trait bounds`() = testExpr("""
        #[lang = "deref"]
        trait Deref { type Target; }
        struct Lazy<T, F, A, B>{t: T, f: F, a: A, b: B }
        struct Foo;
        trait Bound<T, A, B> {}
        trait Bound2<T> {}
        impl<A, B> Bound<(A, B), A, B> for Foo {}

        impl<T, A, B, F> Lazy<T, F, A, B> {
            fn new(_: F) -> Lazy<T, F, A, B> { todo!() }
        }

        impl<T, A, B, F> Deref for Lazy<T, F, A, B>
            where
                F: Bound<T, A, B>,
                A: Bound2<B>
        {
            type Target = T;
        }

        struct Bar;
        struct Baz;
        impl Bound2<Baz> for Bar {}
        fn foo() {
            let l = Lazy::new(Foo);
            let a = l.0;
            let b = l.1;
            let _: Bar = a;
            b;
        } //^ Baz
    """)

    fun `test explicit deref with trait bounds`() = testExpr("""
        #[lang = "deref"]
        trait Deref { type Target; }

        struct Lazy<T, F, A, B>(T, F, A, B);
        struct Foo;
        trait Bound<T, A, B> {}
        trait Bound2<T> {}
        impl<A, B> Bound<S<A, B>, A, B> for Foo {}

        impl<T, A, B, F> Lazy<T, F, A, B> {
            fn new(_: F) -> Lazy<T, F, A, B> { todo!() }
        }

        impl<T, A, B, F> Deref for Lazy<T, F, A, B>
            where
                F: Bound<T, A, B>,
                A: Bound2<B>
        {
            type Target = T;
        }

        struct S<A, B>(A, B);

        struct Bar;
        struct Baz;
        impl Bound2<Baz> for Bar {}
        fn foo() {
            let l = Lazy::new(Foo);
            let s = &(*l);
            func(s);
            &s.1;
        } //^ &Baz

        fn func<B>(s: &S<Bar, B>) {}
    """)

    fun `test deref coercion with trait bounds`() = testExpr("""
        #[lang = "deref"]
        trait Deref { type Target; }

        struct Lazy<T, F, A, B>(T, F, A, B);
        struct Foo;
        trait Bound<T, A, B> {}
        trait Bound2<T> {}
        impl<A, B> Bound<S<A, B>, A, B> for Foo {}

        impl<T, F, A, B> Lazy<T, F, A, B> {
            fn new(_: F) -> Lazy<T, F, A, B> { todo!() }
        }

        impl<T, F, A, B> Deref for Lazy<T, F, A, B> where A: Bound2<B>, F: Bound<T, A, B> {
            type Target = T;
        }

        struct S<A, B>(A, B);

        struct Bar;
        struct Baz;
        impl Bound2<Baz> for Bar {}
        fn foo() {
            let l = Lazy::new(Foo);
            let s: &S<Bar, _> = &l;
            s.1;
        }   //^ Baz
    """)

    fun `test deref index access with trait bounds`() = testExpr("""
        #[lang = "deref"]
        trait Deref { type Target; }

        #[lang = "index"]
        trait Index<Idx> { type Output; }

        struct Lazy<T, F, A, B>(T, F, A, B);
        struct Foo;
        trait Bound<T, A, B> {}
        trait Bound2<T> {}
        impl<A, B> Bound<S<A, B>, A, B> for Foo {}

        impl<T, F, A, B> Lazy<T, F, A, B> {
            fn new(_: F) -> Lazy<T, F, A, B> { todo!() }
        }

        impl<T, F, A, B> Deref for Lazy<T, F, A, B> where A: Bound2<B>, F: Bound<T, A, B> {
            type Target = T;
        }

        impl<A, B> Index<i32> for S<A, B> {
            type Output = (A, B);
        }

        struct S<A, B>(A, B);

        struct Bar;
        struct Baz;
        impl Bound2<Baz> for Bar {}
        fn foo() {
            let l = Lazy::new(Foo);
            let (a, b) = l[0];
            let _: Bar = a;
            b;
        } //^ Baz
    """)

    fun `test deref call expr with trait bounds`() = testExpr("""
        #[lang = "deref"]
        trait Deref { type Target; }

        #[lang = "fn_once"]
        trait FnOnce<Args> { type Output; }

        struct Lazy<T: ?Sized, F, A, B>(Box<T>, F, A, B);
        struct Foo;
        trait Bound<T: ?Sized, A, B> {}
        trait Bound2<T> {}
        impl<A, B> Bound<dyn FnOnce() -> (A, B), A, B> for Foo {}

        impl<T: ?Sized, F, A, B> Lazy<T, F, A, B> {
            fn new(_: F) -> Lazy<T, F, A, B> { todo!() }
        }

        impl<T: ?Sized, F, A, B> Deref for Lazy<T, F, A, B> where A: Bound2<B>, F: Bound<T, A, B> {
            type Target = T;
        }
        struct Bar;
        struct Baz;
        impl Bound2<Baz> for Bar {}

        fn foo() {
            let l = Lazy::new(Foo);
            let (a, b) = l();
            let _: Bar = a;
            b;
        } //^ Baz
    """)

    @CheckTestmarkHit(TypeInferenceMarks.WinnowParamCandidateWins::class)
    fun `test assoc type bound does not conflict with type bound`() = testExpr("""
        struct X;
        trait Foo<T> {}
        fn foo<A: Foo<B>, B>(_: A) -> B { unimplemented!() }
        trait Bar { type Item: Foo<X>; }
        fn bar<T: Bar>(_: T, b: T::Item) where T::Item: Foo<X> {
            let a = foo(b);
            a;
        } //^ X
    """)

    @CheckTestmarkHit(TypeInferenceMarks.WinnowObjectOrProjectionCandidateWins::class)
    fun `test assoc type bound wins over blanket impl`() = testExpr("""
        struct X;
        trait Foo<T> {}
        impl<T> Foo<X> for T {}
        fn foo<A: Foo<B>, B>(_: A) -> B { unimplemented!() }
        trait Bar { type Item: Foo<X>; }
        fn bar<T: Bar>(_: T, b: T::Item) {
            let a = foo(b);
            a;
        } //^ X
    """)

    fun `test generic assoc type bound selection 1`() = testExpr("""
        struct X;
        trait Foo<T> {}
        fn foo<A: Foo<B>, B>(_: A) -> B { unimplemented!() }
        trait Bar { type Item<T>: Foo<T>; }
        fn bar<T: Bar>(_: T, b: T::Item<X>) {
            let a = foo(b);
            a;
        } //^ X
    """)

    fun `test generic assoc type bound selection 2`() = testExpr("""
        struct X;
        trait Foo { type Item<T>: Bar1<T> + Bar2<T>; }
        trait Bar1<T>: Baz<T> {}
        trait Bar2<T>: Baz<T> {}
        trait Baz<T> {}
        fn baz<A: Baz<B>, B>(t: A) -> B { unimplemented!() }

        fn foobar<T: Foo>(a: T::Item<X>) {
            let b = baz(a);
            b;
        } //^ X
    """)

    fun `test infer type parameter from associated type binding`() = testExpr("""
        trait Foo { type Item; }
        fn foo<A, B>(a: A) -> B where A: Foo<Item = B> { unimplemented!() }

        struct S;
        impl Foo for S { type Item = i32; }

        fn main() {
            let a = foo(S);
            a;
        } //^ i32
    """)

    fun `test infer type parameter from blank impl 1`() = testExpr("""
        struct S;
        trait Foo<O> {}

        impl<T> Foo<S> for T {}

        fn foo<T: Foo<B>, B>(t: T) -> B {
            todo!()
        }

        fn bar<T>(t: T) {
            let a = foo(t);
            a;
        } //^ S
    """)

    fun `test infer type parameter from blank impl 2`() = testExpr("""
        struct S;
        trait Foo<O> {}

        impl<T> Foo<S> for T {}

        fn foo<T: Foo<B>, B>(t: T) -> B {
            todo!()
        }

        fn bar<T: Foo<S>>(t: T) {
            let a = foo(t);
            a;
        } //^ S
    """)

    @CheckTestmarkHit(TypeInferenceMarks.WinnowParamCandidateWins::class)
    fun `test infer type parameter from blank impl 3`() = testExpr("""
        struct W<T>(T);
        struct S;
        trait Foo<O> {}
        impl<T> Foo<S> for W<T> {}
        fn foo<T: Foo<B>, B>(t: T) -> B { todo!() }
        fn bar<T>(t: W<T>) where W<T>: Foo<S> {
            let a = foo(t);
            a;
        } //^ S
    """)

    @CheckTestmarkHit(TypeInferenceMarks.WinnowParamCandidateLoses::class)
    fun `test global type bound does not affect type inference`() = testExpr("""
        struct S;
        struct X;
        trait Foo<O> {}
        impl Foo<X> for S {}
        fn foo<T: Foo<B>, B>(t: T) -> B { todo!() }
        fn bar(t: S) where S: Foo<X> {
            let a = foo(t);
            a;
        } //^ X
    """)

    @CheckTestmarkHit(TypeInferenceMarks.WinnowObjectOrProjectionCandidateWins::class)
    fun `test trait object wins over blank impl`() = testExpr("""
        struct S;
        trait Foo<O> {}
        impl<T> Foo<S> for T {}
        fn foo<T: Foo<B> + ?Sized, B>() -> B { todo!() }
        fn bar() {
            let a = foo::<dyn Foo<S>, _>();
            a;
        } //^ S
    """)

    @CheckTestmarkHit(TypeInferenceMarks.WinnowObjectOrProjectionCandidateWins::class)
    fun `test 'impl Trait' wins over blank impl`() = testExpr("""
        struct S;
        trait Foo<O> {}
        impl<T> Foo<S> for T {}
        fn foo<T: Foo<B>, B>(t: T) -> B { todo!() }
        fn impl_foo() -> impl Foo<S> { todo!() }
        fn bar() {
            let a = foo(impl_foo());
            a;
        } //^ S
    """)

    // https://github.com/intellij-rust/intellij-rust/issues/5897
    fun `test issue 5897`() = testExpr("""
        trait Index<Idx: ?Sized> {
            type Output: ?Sized;
            fn index(&self, index: Idx) -> &Self::Output;
        }

        struct Slice1<T>(T);
        impl<T> Index<usize> for Slice1<T> {
            type Output = T;
            fn index(&self, _: usize) -> &Self::Output { todo!() }
        }

        struct Slice2;
        impl Index<usize> for Slice2 {
            type Output = <Slice1<u32> as Index<usize>>::Output;
            fn index(&self, _: usize) -> &Self::Output { todo!() }
        }
        fn bar(x: Slice2) {
            let a = *x.index(0);
            a;
        } //^ u32
    """)

    fun `test select impl with associated type projection through type alias`() = testExpr("""
        struct A;
        pub trait Trait<T> { type Item; }
        impl Trait<i32> for A { type Item = u32; }
        impl Trait<i8> for A { type Item = u8; }

        pub type Unsigned<T> = <A as Trait<T>>::Item;

        struct B<T>(T);
        trait Trait2 {
            type Item2;
        }
        impl Trait2 for B<Unsigned<i32>> {
            type Item2 = u32;
        }
        impl Trait2 for B<Unsigned<i8>> {
            type Item2 = u8;
        }
        fn foo<C: Trait2>(_: C) -> C::Item2 { unimplemented!() }
        fn bar(a: B<Unsigned<i8>>) {
            let a = foo(a);
            a;
        } //^ u8
    """)

    // Issue https://github.com/intellij-rust/intellij-rust/issues/8236
    fun `test select impl with associated type projection in trait ref`() = testExpr("""
        struct S;
        struct X;
        trait Trait { type Item; }
        impl Trait for S { type Item = X; }

        trait Bound<A> {}
        impl Bound<<S as Trait>::Item> for S {}

        fn foo<B, C>(_: B) -> C where B: Bound<C> { todo!() }
        fn main() {
            let a = foo(S);
            a;
        } //^ X
    """)

    fun `test trait impl with default type parameter value`() = testExpr("""
        struct X;
        trait Tr<T = X> { fn foo(&self) -> T { todo!() } }
        struct S;
        impl Tr for S {}
        fn foo() {
            let a = S.foo();
            a;
        } //^ X
    """)

    fun `test trait impl with default const parameter value`() = testExpr("""
        trait Tr<const T: usize = 1> { fn foo(&self) -> [i32; T] { todo!() } }

        struct S;
        impl Tr for S {}

        fn foo() {
            let a = S.foo();
            a;
        } //^ [i32; 1]
    """)

    @CheckTestmarkHit(TypeInferenceMarks.TraitSelectionOverflow::class)
    fun `test recursion limit for associated type projection`() = testExpr("""
        trait Trait { type Item; }

        struct W<T>(T);
        impl<T: Trait> Trait for W<T> {
            type Item = T::Item;
        }

        struct X;
        impl Trait for X {
            type Item = i32;
        }

        // More than 128-level nesting
        type T = W<W<W<W<W<W<W<W<W<W<W<W<W<W<W<W<W<W<W<W<W<W<W<W<W<W<W<W<W<W<W<W<
            W<W<W<W<W<W<W<W<W<W<W<W<W<W<W<W<W<W<W<W<W<W<W<W<W<W<W<W<W<W<W<W<W<
                W<W<W<W<W<W<W<W<W<W<W<W<W<W<W<W<W<W<W<W<W<W<W<W<W<W<W<W<W<W<W<W<
                    W<W<W<W<W<W<W<W<W<W<W<W<W<W<W<W<W<W<W<W<W<W<W<W<W<W<W<W<W<W<W<W<W<
                        X
                    >>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>
                >>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>
            >>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>
        >>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>;

        fn foo(a: <T as Trait>::Item) {
            a;
        } //^ <unknown>
    """)

    @CheckTestmarkHit(TypeInferenceMarks.TraitSelectionOverflow::class)
    fun `test recursion limit for trait selection`() = testExpr("""
        trait Trait<T> {}

        struct W<T>(T);
        impl<A: Trait<B>, B> Trait<B> for W<A> {}

        struct X;
        impl Trait<i32> for X {}

        // More than 128-level nesting
        type T = W<W<W<W<W<W<W<W<W<W<W<W<W<W<W<W<W<W<W<W<W<W<W<W<W<W<W<W<W<W<W<W<
            W<W<W<W<W<W<W<W<W<W<W<W<W<W<W<W<W<W<W<W<W<W<W<W<W<W<W<W<W<W<W<W<W<
                W<W<W<W<W<W<W<W<W<W<W<W<W<W<W<W<W<W<W<W<W<W<W<W<W<W<W<W<W<W<W<W<
                    W<W<W<W<W<W<W<W<W<W<W<W<W<W<W<W<W<W<W<W<W<W<W<W<W<W<W<W<W<W<W<W<W<
                        X
                    >>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>
                >>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>
            >>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>
        >>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>;

        fn foo<A: Trait<B>, B>(_: A) -> B {
            todo!()
        }

        fn bar(a: T) {
            let b = foo(a);
            b;
        } //^ <unknown>
    """)

    fun `test duplicated trait bounds`() = testExpr("""
        struct S;
        struct X;
        trait Foo<O> {}

        fn foo<T: Foo<B>, B>(t: T) -> B {
            todo!()
        }

        fn bar<T: Foo<X> + Foo<X>>(t: T) {
            let a = foo(t);
            a;
        } //^ X
    """)

    fun `test ignore impl in independent crate`() = stubOnlyTypeInfer("""
    //- lib.rs
        pub struct Foo;
        pub trait Bar {
            type Item;
        }

        impl Bar for Foo {
            type Item = i32;
        }

        fn infer<T: Bar>(_: T) -> T::Item { todo!() }

        fn foo() {
            let a = infer(Foo);
            a;
        } //^ i32
    //- main.rs
        use test_package::*;

        impl Bar for Foo { // Incoherent impl
            type Item = u8;
        }
    """)

    fun `test type placeholder in a Type as Trait UFCS qualified associated type path`() = testExpr("""
        struct Foo<T>(T);

        trait Bar {
            type SelfTy;
        }

        impl<T> Bar for Foo<T> {
            type SelfTy = Foo<T>;
        }

        fn baz() {
            let a: <Foo<_> as Bar>::SelfTy = Foo(1);
            a;
        } //^ Foo<i32>
    """)

    fun `test select a supertrait with Self subst 1`() = testExpr("""
        trait Foo<T: ?Sized> {}
        trait Bar<T: ?Sized>: Foo<T> {}

        trait Baz: Sized {
            fn bar<T: Bar<Self>>(t: T) {
                let a = foo(t);
                a;
            } //^ Self
        }

        fn foo<A: Foo<B>, B>(t: A) -> B { todo!() }
    """)

    fun `test select a supertrait with Self subst 2`() = testExpr("""
        trait Foo {
            type Item;
        }
        trait Bar: Foo {}

        trait Baz: Sized {
            fn bar<T: Bar<Item=Self>>(t: T) {
                let a = foo(t);
                a;
            } //^ Self
        }

        fn foo<A: Foo>(t: A) -> A::Item { todo!() }
    """)

    fun `test select a supertrait with Self subst 3`() = testExpr("""
        trait Foo<T: ?Sized> {}
        trait Bar: Foo<Self> {}

        fn bar<T: Bar>(t: T) {
            let a = foo(t);
            a;
        } //^ T

        fn foo<A: Foo<B>, B>(t: A) -> B { todo!() }
    """)

    @CheckTestmarkHit(TypeInferenceMarks.RecursiveProjectionNormalization::class)
    fun `test recursive projection in impl type`() = testExpr("""
        struct S<T>(T);
        trait Trait { type Item; }
        impl Trait for S<<S<()> as Trait>::Item> { type Item = (); }
        fn foo<T: Trait>(_: T) -> T::Item { todo!() }
        fn bar(a: S<()>) {
            let b = foo(a);
            b;
        } //^ <unknown>
    """)

    @CheckTestmarkHit(TypeInferenceMarks.RecursiveProjectionNormalization::class)
    fun `test recursive projection in impl trait ref`() = testExpr("""
        struct S;
        trait Trait<T> { type Item; }
        impl Trait<<S as Trait<()>>::Item> for S { type Item = (); }
        fn foo<A: Trait<B>, B>(_: A) -> B { todo!() }
        fn bar(a: S) {
            let b = foo(a);
            b;
        } //^ <unknown>
    """)

    fun `test associated type projection in impl trait ref`() = testExpr("""
        struct S;
        struct X;
        trait Foo { type Item; }
        impl Foo for S { type Item = X; }

        trait Bar<T> {}
        impl Bar<<S as Foo>::Item> for S {}
        fn foo<A: Bar<B>, B>(_: A) -> B { todo!() }
        fn bar(a: S) {
            let b = foo(a);
            b;
        } //^ X
    """)

    fun `test associated type projection in impl trait ref via type parameter default`() = testExpr("""
        struct S;
        struct X;
        trait Foo { type Item; }
        impl Foo for S { type Item = X; }

        trait Bar<T = <Self as Foo>::Item> {}
        impl Bar for S {}
        fn foo<A: Bar<B>, B>(_: A) -> B { todo!() }
        fn bar(a: S) {
            let b = foo(a);
            b;
        } //^ X
    """)

    fun `test method from generic assoc type bound`() = testExpr("""
        trait Foo { type Item<A>: Bar<A>; }
        trait Bar<B>: Baz<B> {}
        trait Baz<C> {
            fn baz(&self) -> C { todo!() }
        }
        fn foobar<D: Foo>(a: D::Item<i32>) {
            let b = a.baz();
            b;
        } //^ i32
    """)
}
