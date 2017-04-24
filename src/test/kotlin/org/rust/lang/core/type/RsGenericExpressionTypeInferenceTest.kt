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

    fun testGenericFieldReference() = testExpr("""
        struct S<'a, T> { field: &'a T }

        fn foo(s: S<'static, f64>) {
            let x = s.field;
            x
          //^ & f64
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

    fun testGenericArray() = testExpr("""
        struct S<T> { field: [T; 1] }

        fn foo(s: S<f64>) {
            let x = s.field;
            x
          //^ [f64; 1]
        }
    """)

    fun testGenericSlice() = testExpr("""
        struct S<T: 'static> { field: &'static [T] }

        fn foo(s: S<f64>) {
            let x = s.field;
            x
          //^ & [f64]
        }
    """)

    fun testGenericConstPtr() = testExpr("""
        struct S<T> { field: *const T }

        fn foo(s: S<f64>) {
            let x = s.field;
            x
          //^ *const f64
        }
    """)

    fun testGenericMutPtr() = testExpr("""
        struct S<T> { field: *mut T }

        fn foo(s: S<f64>) {
            let x = s.field;
            x
          //^ *mut f64
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

    fun `test struct expr`() = testExpr("""
        struct S<T> { a: T }
        fn main() {
            let x = S { a: 5u16 };
            x.a
            //^ u16
        }
    """)

    fun `test struct expr with 2 fields of same type integer 1`() = testExpr("""
        struct S<T> { a: T, b: T }
        fn main() {
            let x = S { a: 5u16, b: 0 };
            x.b
            //^ u16
        }
    """)

    fun `test struct expr with 2 fields of same type integer 2`() = testExpr("""
        struct S<T> { a: T, b: T }
        fn main() {
            let x = S { a: 0, b: 5u16 };
            x.a
            //^ u16
        }
    """)

    fun `test struct expr with 2 fields of same type float 2`() = testExpr("""
        struct S<T> { a: T, b: T }
        fn main() {
            let x = S { a: 0.0, b: 5f32 };
            x.a
            //^ f32
        }
    """)

    fun `test struct expr with 2 fields of different types`() = testExpr("""
        struct S<T1, T2> { a: T1, b: T2 }
        fn main() {
            let x = S { a: 5u16, b: 5u8 };
            (x.a, x.b)
          //^ (u16, u8)
        }
    """)

    fun testTupleStructExpression() = testExpr("""
        struct S<T> (T);
        fn main() {
            let x = S(5u16);
            x.0
            //^ u16
        }
    """)

    fun testGenericAlias() = testExpr("""
        struct S1<T>(T);
        struct S3<T1, T2, T3>(T1, T2, T3);

        type A<T1, T2> = S3<T2, S1<T1>, S3<S1<T2>, T2, T2>>;
        type B = A<u16, u8>;

        fn f(b: B) {
            (b.0, (b.1).0, ((b.2).0).0)
          //^ (u8, u16, u8)
        }
    """)
}

