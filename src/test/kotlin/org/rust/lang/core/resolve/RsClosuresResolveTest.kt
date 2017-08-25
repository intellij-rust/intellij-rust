/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.resolve

import org.intellij.lang.annotations.Language

class RsClosuresResolveTest : RsResolveTestBase() {
    override fun checkByCode(@Language("Rust") code: String) {
        val FN_LANG_ITEMS = """
            #[lang = "fn_once"]
            trait FnOnce<Args> { type Output; }

            #[lang = "fn_mut"]
            trait FnMut<Args>: FnOnce<Args> { }

            #[lang = "fn"]
            trait Fn<Args>: FnMut<Args> { }
        """
        super.checkByCode("$FN_LANG_ITEMS\n\n$code")
    }

    fun `test simple method resolve for closure`() = checkByCode("""
        struct T;
        impl T {
            fn bar(&self) {}
             //X
        }

        fn foo<F: Fn(&T) -> ()>(f: F) {}

        fn main() {
            foo(|t| { t.bar(); })
        }              //^
    """)

    fun `test wrong closure parameter`() = checkByCode("""
        struct T;
        impl T {
            fn bar(&self) {}
        }

        fn foo<F: Fn(&T) -> ()>(f: F) {}

        fn main() {
            foo(None, |t| { t.bar(); })
        }                    //^ unresolved
    """)

    fun `test simple method resolve with where for closure`() = checkByCode("""
        struct T;
        impl T {
            fn bar(&self) {}
             //X
        }

        fn foo<F>(f: F) where F: Fn(&T) -> () {}

        fn main() {
            foo(|t| { t.bar(); })
        }              //^
    """)

    fun `test simple self resolve for closure`() = checkByCode("""
        struct T;
        impl T {
            fn bar(&self) {}
              //X
            fn foo<F: Fn(&T) -> ()>(&self, f: F) {}
        }

        fn main() {
            let t = T;
            t.foo(|e| { e.bar(); })
        }                //^
    """)

    fun `test multi self resolve for closure`() = checkByCode("""
        struct T;
        impl T {
            fn bar(&self) {}
              //X
            fn foo<F: Fn(&T) -> ()>(&self, f: F) {}
        }

        struct S;
        impl S { fn bar(&self) -> T { T } }

        fn main() {
            S.bar().foo(|t| t.bar());

            T.foo(|t| t.bar());
        }              //^
    """)

    fun `test associated type resolve for closure`() = checkByCode("""
        trait Iter {
            type Item;
            fn filter<P>(self, predicate: P) -> Filter<Self, P> where Self: Sized, P: FnMut(&Self::Item) {}
        }

        struct Foo;
        impl Foo {
            fn bar(&self) {}
              //X
        }

        struct S;
        impl Iter for S {
            type Item = Foo;
        }

        fn main() {
            let t = S;
            t.filter(|e| { e.bar(); })
        }                   //^
    """)

    fun `test apply`() = checkByCode("""
        struct S;
        impl S { fn foo(&self) {} }
                   //X

        fn call<F: Fn() -> S>(f: F) {
            f().foo()
        }      //^
    """)

    fun `test generic trait method`() = checkByCode("""
        struct S<T1>(T1);

        trait Foo<T2> { fn foo<F: FnOnce(T2)>(&self, f: F) {} }
        impl<T3> Foo<T3> for S<T3> {}

        struct X;
        impl X { fn fox(&self) {} }
                   //X

        fn main() {
            S(X).foo(|x| {
                x.fox();
            });  //^
        }
    """)

    fun `test lambda in generic expression with function type`() = checkByCode("""
        struct S;
        impl S { fn bar(&self) {} }
                   //X

        fn with_s<F: Fn(S)>(f: F) {}

        fn main() {
            (with_s)(|s| s.bar())
        }                 //^
    """)

    fun `test layered visitor example`() = checkByCode("""
        struct PhantomData;

        trait NodeVisitor<'f, C> {
            fn visit<T, F>(self, f: F) -> AstVisitor<Self, T, F>
                where Self: Sized, F: FnMut(&mut C, T) {
                AstVisitor { visitor: self, f: f, t: PhantomData }
            }
        }

        pub struct Visitor<C>(pub C);

        impl<'f, C> NodeVisitor<'f, C> for Visitor<C> {}

        pub struct AstVisitor<V, T, F> {
            visitor: V,
            f: F,
            t: PhantomData<*const T>
        }

        impl<'f, C, V, T, F> NodeVisitor<'f, C> for AstVisitor<V, T, F>
            where V: NodeVisitor<'f, C>, F: FnMut(&mut C, T)
        {}

        struct X;
        impl X { fn foo(&self) {} }
                   //X
        fn main() {
            let mut ctx = X;
            Visitor(&mut ctx)
                .visit::<(), _>(|ctx, t| ctx.foo())
                .visit::<(), _>(|ctx, t| ctx.foo())
            ;                               //^
        }
    """)

    // This test failed with OOME due to the bug in associated types inference
    fun `test futures example`() = checkByCode("""
        fn foo<F: Future>(f: F) {
            f.and_then(|x| x).and_then(|x| x).and_then(|x| x)
        }                                   //^

        trait Future {
            type Item;
            type Error;

            fn and_then<F, B>(self, f: F) -> AndThen<Self, B, F>
                //X
                where F: FnOnce(Self::Item) -> B,
                      B: IntoFuture<Error = Self::Error>,
                      Self: Sized,
            { unimplemented!() }
        }

        pub trait IntoFuture {
            type Future: Future<Item=Self::Item, Error=Self::Error>;
            type Item;
            type Error;

            fn into_future(self) -> Self::Future;
        }

        pub struct AndThen<A, B, F> where A: Future, B: IntoFuture {
            state: (A, B::Future, F)
        }

        impl<A, B, F> Future for AndThen<A, B, F>
            where A: Future,
                  B: IntoFuture<Error=A::Error>,
                  F: FnOnce(A::Item) -> B,
        {
            type Item = B::Item;
            type Error = B::Error;
        }
    """)

    fun `test infer generic parameter from lambda return type by fn pointer`() = checkByCode("""
        struct X;
        impl X { fn foo(&self) {} }
                   //X
        fn apply<T1, T2>(t: T1, f: fn(T1) -> T2) -> T2 { f(t) }
        fn main() {
            let a = apply(X, |x| x);
            a.foo()
        }   //^
    """)

    fun `test infer generic parameter from lambda return type 1`() = checkByCode("""
        struct X;
        impl X { fn foo(&self) {} }
                   //X
        fn apply<T1, T2, F: Fn(T1) -> T2>(t: T1, f: F) -> T2 { f(t) }
        fn main() {
            let a = apply(X, |x| x);
            a.foo()
        }   //^
    """)

    fun `test infer generic parameter from lambda return type 2`() = checkByCode("""
        struct X;
        impl X { fn foo(&self) {} }
                   //X
        fn apply<T1, T2, F: Fn(T1) -> T2>(f: F, t: T1) -> T2 { f(t) }
        fn main() {
            let a = apply(|x| x, X);
            a.foo()
        }   //^
    """)

    fun `test infer lambda argument type from next method argument`() = checkByCode("""
        struct X;
        impl X {
            fn foo(self) -> X { self }
            fn bar(&self) {}
        }    //X
        fn apply<T1, T2, F: Fn(T1) -> T2>(f: F, t: T1) -> T2 { f(t) }
        fn main() {
            apply((|x| x.foo().bar()), X);
        }                    //^
    """)

    fun `test infer argument type from next method argument 2`() = checkByCode("""
        struct X;
        impl X {
            fn foo(self) -> X { self }
            fn bar(&self) {}
        }    //X
        fn apply<T1, T2, T3, F1, F2>(f1: F1, f2: F2, t: T1) -> T3
            where F1: Fn(T1) -> T2,
                  F2: Fn(T2) -> T3,
        {
            f2(f1(t))
        }
        fn main() {
            apply(|x| x, |x| x.foo().bar(), X);
        }                          //^
    """)
}
