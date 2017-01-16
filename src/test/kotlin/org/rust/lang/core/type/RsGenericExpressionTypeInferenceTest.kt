package org.rust.lang.core.type

class RsGenericExpressionTypeInferenceTest : RsTypificationTestBase() {
    fun testGenericField() = testExpr("""
        struct S<T> { field: T }

        fn foo(s: S<f64>) {
            let x = s.field;
            x
          //^ f64
        }
    """)

    fun testNestedGenericField() = testExpr("""
        struct A<T> { field: T }
        struct B<S> { field: S }

        fn foo(s: A<B<f64>>) {
            let x = s.field.field;
            x
          //^ f64
        }
    """)

    fun testNestedGenericField2() = testExpr("""
        struct S<T> { field: T }

        fn foo(s: S<S<u64>>) {
            let x = s.field.field;
            x
          //^ u64
        }
    """)

    fun testGenericMethod() = testExpr("""
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

    fun testTwoParameters() = testExpr("""
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

    fun testParamSwap() = testExpr("""
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

    fun testParamRepeat() = testExpr("""
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

    fun testPartialSpec() = testExpr("""
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

    fun testRecursiveType() = testExpr("""
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

    fun testSelfType() = testExpr("""
        trait T {
            fn foo(&self) { self; }
                            //^ & Self
        }
    """)
}

