/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.type

import org.rust.lang.core.types.infer.TypeInferenceMarks

class RsGenericExpressionTypeInferenceTest : RsTypificationTestBase() {
    fun `test generic field`() = testExpr("""
        struct S<T> { field: T }

        fn foo(s: S<f64>) {
            let x = s.field;
            x
          //^ f64
        }
    """)

    fun `test generic field reference`() = testExpr("""
        struct S<'a, T> { field: &'a T }

        fn foo(s: S<'static, f64>) {
            let x = s.field;
            x
          //^ &f64
        }
    """)

    fun `test nested generic field`() = testExpr("""
        struct A<T> { field: T }
        struct B<S> { field: S }

        fn foo(s: A<B<f64>>) {
            let x = s.field.field;
            x
          //^ f64
        }
    """)

    fun `test nested generic field 2`() = testExpr("""
        struct S<T> { field: T }

        fn foo(s: S<S<u64>>) {
            let x = s.field.field;
            x
          //^ u64
        }
    """)

    fun `test generic array`() = testExpr("""
        struct S<T> { field: [T; 1] }

        fn foo(s: S<f64>) {
            let x = s.field;
            x
          //^ [f64; 1]
        }
    """)

    fun `test generic slice`() = testExpr("""
        struct S<T: 'static> { field: &'static [T] }

        fn foo(s: S<f64>) {
            let x = s.field;
            x
          //^ &[f64]
        }
    """)

    fun `test generic const ptr`() = testExpr("""
        struct S<T> { field: *const T }

        fn foo(s: S<f64>) {
            let x = s.field;
            x
          //^ *const f64
        }
    """)

    fun `test generic mut ptr`() = testExpr("""
        struct S<T> { field: *mut T }

        fn foo(s: S<f64>) {
            let x = s.field;
            x
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
            x
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
             x
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
            x
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
            x
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
            x
          //^ ()
        }
    """)

    fun `test generic function`() = testExpr("""
        fn f<T>(t: T) -> T { t }

        fn main() {
            let a = f(0i32);
            a
          //^ i32
        }
    """)

    fun `test generic function 2`() = testExpr("""
        fn f<T2, T1>(t1: T1, t2: T2) -> (T1, T2) { (t1, t2) }

        fn main() {
            let a = f(0u8, 1u16);
            a
          //^ (u8, u16)
        }
    """)

    fun `test generic function 3`() = testExpr("""
        fn f<T>(t: T) -> T { t }

        fn main() {
            let a = f::<u8>(1);
            a
          //^ u8
        }
    """)

    fun `test generic function pointer`() = testExpr("""
        fn f<T>(t: T) -> T { t }

        fn main() {
            let f = f::<u8>;
            let r = f(1);
            r
          //^ u8
        }
    """)

    fun `test generic function pointer 2`() = testExpr("""
        fn f<T1, T2>(t1: T1, t2: T2) -> (T1, T2) { (t1, t2) }

        fn main() {
            let f = f::<u8, _>;
            let r = f(1, 2);
            r
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
            a.value
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
            x
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
            x.a
            //^ u16
        }
    """)

    fun `test struct expr with 2 fields of same type 1`() = testExpr("""
        struct X;
        struct S<T> { a: T, b: T }
        fn main() {
            let x = S { a: X, b: unimplemented!() };
            x.b
            //^ X
        }
    """)

    // TODO fix `!` unification and replace `0` to `unimplemented!()`
    fun `test struct expr with 2 fields of same type 2`() = testExpr("""
        struct S<T> { a: T, b: T }
        fn main() {
            let x = S { a: 0, b: 1u8 };
            x.a
            //^ u8
        }
    """)

    fun `test struct expr with 2 fields of different types`() = testExpr("""
        struct X; struct Y;
        struct S<T1, T2> { a: T1, b: T2 }
        fn main() {
            let x = S { a: X, b: Y };
            (x.a, x.b)
          //^ (X, Y)
        }
    """)

    fun `test struct expr with explicit type parameter`() = testExpr("""
        struct S<T> {a: T}
        fn main() {
            let x = S::<u8>{a: 1};
            x.a
            //^ u8
        }
    """)

    fun `test struct expr with explicit and omitted type parameter`() = testExpr("""
        struct S<T1, T2> {a: T1, b: T2}
        fn main() {
            let x = S::<u8, _>{a: 1, b: 2};
            (x.a, x.b)
          //^ (u8, i32)
        }
    """)

    fun `test tuple struct expression`() = testExpr("""
        struct S<T> (T);
        fn main() {
            let x = S(5u16);
            x.0
            //^ u16
        }
    """)

    fun `test tuple struct expr with explicit type parameter`() = testExpr("""
        struct S<T> (T);
        fn main() {
            let x = S::<u8>(1);
            x.0
            //^ u8
        }
    """)

    fun `test tuple struct expr with explicit and omitted type parameter`() = testExpr("""
        struct S<T1, T2> (T1, T2);
        fn main() {
            let x = S::<u8, _>(1, 2);
            (x.0, x.1)
          //^ (u8, i32)
        }
    """)

    fun `test reference to generic tuple constructor`() = testExpr("""
        struct S<T>(T);
        fn main() {
            let f = S::<u8>;
            f(1).0
        }      //^ u8
    """)

    fun `test struct new with type parameters`() = testExpr("""
        struct Foo<F>(F);
        impl<T> Foo<T> {
            fn new() -> Self { loop {} }
        }
        fn main() {
            let x = Foo::<u16>::new();
            x
          //^ Foo<u16>
        }
    """)

    fun `test generic alias`() = testExpr("""
        struct S1<T>(T);
        struct S3<T1, T2, T3>(T1, T2, T3);

        type A<T1, T2> = S3<T2, S1<T1>, S3<S1<T2>, T2, T2>>;
        type B = A<u16, u8>;

        fn f(b: B) {
            (b.0, (b.1).0, ((b.2).0).0)
          //^ (u8, u16, u8)
        }
    """)

    fun `test struct with alias 1`() = testExpr("""
        struct S<T> { a: T }
        type T1 = S<u8>;
        type T2 = T1;
        fn main() {
            T2 { a: 1 }
        } //^ S<u8>
    """)

    fun `test struct with alias 2`() = testExpr("""
        struct S<T> { a: T }
        type T1<U> = S<U>;
        type T2 = T1<u8>;
        fn main() {
            T2 { a: 1 }
        } //^ S<u8>
    """)

    fun `test struct with alias 3`() = testExpr("""
        struct S<T> { a: T }
        type T1<U> = S<U>;
        type T2<V> = T1<V>;
        fn main() {
            T2 { a: 1u8 }
        } //^ S<u8>
    """)

    fun `test generic struct arg`() = testExpr("""
        struct Foo<F>(F);
        fn foo<T>(xs: Foo<T>) -> T { unimplemented!() }
        fn main() {
            let x = foo(Foo(123));
            x
          //^ i32
        }
    """)

    fun `test generic enum arg`() = testExpr("""
        enum Foo<F> { V(F) }
        fn foo<T>(xs: Foo<T>) -> T { unimplemented!() }
        fn main() {
            let x = foo(Foo::V(123));
            x
          //^ i32
        }
    """)

    fun `test generic tuple arg`() = testExpr("""
        fn foo<T, F>(xs: (T, F)) -> F { unimplemented!() }
        fn main() {
            let x = foo((123, "str"));
            x
          //^ &str
        }
    """)

    fun `test generic reference arg`() = testExpr("""
        fn foo<T>(xs: &T) -> T { unimplemented!() }
        fn main() {
            let x = foo(&8u64);
            x
          //^ u64
        }
    """)

    fun `test generic pointer arg`() = testExpr("""
        fn foo<T>(xs: *const T) -> T { unimplemented!() }
        fn main() {
            let x = foo(&8u16 as *const u16);
            x
          //^ u16
        }
    """)

    fun `test generic array arg`() = testExpr("""
        fn foo<T>(xs: [T; 4]) -> T { unimplemented!() }
        fn main() {
            let x = foo([1, 2, 3, 4]);
            x
          //^ i32
        }
    """)

    fun `test generic slice arg`() = testExpr("""
        fn foo<T>(xs: &[T]) -> T { unimplemented!() }
        fn main() {
            let slice: &[&str] = &["foo", "bar"];
            let x = foo(slice);
            x
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
            x
          //^ (Bar<(i32, bool), i32>, Foo<[f64; 3], &str>)
        }
    """)

    fun `test array to slice`() = testExpr("""
        fn foo<T>(xs: &[T]) -> T { unimplemented!() }
        fn main() {
            let x = foo(&[1, 2, 3]);
            x
          //^ i32
        }
    """)

    fun `test array to slice 2`() = testExpr("""
        fn foo<T>(xs: &[T]) -> T { unimplemented!() }
        fn main() {
            let a = [1, 2, 3];
            let x = foo(&a);
            x
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
            x
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
            x
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
            x
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
            x
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
            x
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
            x
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
            x
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
            x
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
            a
        } //^ i32
    """)

    fun `test infer complex generic argument from trait bound`() = testExpr("""
        struct S<A>(A);
        trait Tr<B> { fn foo(&self) -> B; }
        impl<C, D> Tr<(C, D)> for S<(C, D)> { fn foo(&self) -> (C, D) { unimplemented!() } }
        fn bar<E, F, G: Tr<(E, F)>>(b: G) -> (E, F) { b.foo() }
        fn main() {
            let a = bar(S((1u8, 1u16)));
            a
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

    fun `test Self substitution to trait method`() = testExpr("""
        trait Tr<A> { fn wrap(self) -> S<Self> where Self: Sized { unimplemented!() } }
        struct X;
        struct S<C>(C);
        impl<D> Tr<D> for S<D> {}
        fn main() {
            let a = S(X).wrap().wrap().wrap();
            a
        } //^ S<S<S<S<X>>>>
    """)

    fun `test Self substitution to trait method with deref`() = testExpr("""
        trait Tr<A> { fn wrap(self) -> S<Self> where Self: Sized { unimplemented!() } }
        struct X;
        struct S<C>(C);
        impl<D> Tr<D> for S<D> {}
        fn main() {
            let a = (&S(X)).wrap().wrap().wrap();
            a
        } //^ S<S<S<S<X>>>>
    """)

    fun `test Self substitution to impl method`() = testExpr("""
        trait Tr<A> { fn wrap(self) -> S<Self> where Self: Sized { unimplemented!() } }
        struct X;
        struct S<C>(C);
        impl<D> Tr<D> for S<D> { fn wrap(self) -> S<Self> where Self: Sized { unimplemented!() } }
        fn main() {
            let a = S(X).wrap().wrap().wrap();
            a
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
            a
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
            a
        } //^ u8
    """)

    fun `test bound associated type in explicit UFCS form`() = testExpr("""
        trait Tr { type Item; }
        struct S<A>(A);
        impl<B: Tr> S<B> { fn foo(self) -> <B as Tr>::Item { unimplemented!() } }

        struct X;
        impl Tr for X { type Item = u8; }
        fn main() {
            let a = S(X).foo();
            a
        } //^ u8
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
            a
        } //^ u8
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
            a
        } //^ u8
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
            a
        } //^ (u8, u16)
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
            a
        } //^ X
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
            a
        } //^ X
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
            a
        } //^ <T as Tr>::Item
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
            x
          //^ i32
        }
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
            x
          //^ i32
        }
    """)

    fun `test simple unification`() = testExpr("""
        struct S<T>(T);

        fn main() {
            let a: S<_> = S(0i32);
            a
        } //^ S<i32>
    """)

    fun `test simple unification 1`() = testExpr("""
        fn main() {
            let a: _ = 0i32;
            a
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
            a
        } //^ S<u8, u16>
    """)

    fun `test unify struct fields`() = testExpr("""
        struct S<A> {a: A, b: A}
        fn main() {
            S {a: 0, b: 1u8}
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
            (a, b, c)
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
            (a, b, c)
        } //^ (S<X>, S<X>, S<X>)
    """)

    fun `test unification continued on type mismatch`() = testExpr("""
        struct S<A, B>(A, B);
        fn foo<T>(a: T, b: T) {}
        struct X; struct Y;
        fn main() {
            let a = 0;
            foo(S(X, a), S(Y, 1u8));
            a
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

    fun `test infer method arg with multiple impls of the same trait`() = testExpr("""
        pub trait Tr<T> { fn foo(&self, _: T); }
        struct S; struct S1;
        impl Tr<S1> for S { fn foo(&self, _: S1) {} }
        impl Tr<u8> for S { fn foo(&self, _: u8) {} }
        fn main() {
            S.foo(0)
        }       //^ u8
    """, TypeInferenceMarks.methodPickCollapseTraits)

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
    """, TypeInferenceMarks.methodPickCollapseTraits)

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

    fun `test inherited generic type parameter method`() = testExpr("""
        trait Tr1<A> { fn foo(&self) -> A { unimplemented!() } }
        trait Tr2<B>: Tr1<B> {}
        fn bar<T: Tr2<u8>>(t: T) {
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
}
