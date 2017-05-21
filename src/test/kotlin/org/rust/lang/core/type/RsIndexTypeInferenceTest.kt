package org.rust.lang.core.type

class RsIndexTypeInferenceTest : RsTypificationTestBase() {

    fun testIndex() = testExpr("""
        #[lang = "index"]
        pub trait Index<Idx: ?Sized> {
            type Output: ?Sized;
            fn index(&self, index: Idx) -> &Self::Output;
        }

        struct S;

        impl Index<i32> for S {
            type Output = i32;
            fn index(&self, index: i32) -> &i32 {
                unimplemented!()
            }
        }

        fn foo(s: S) {
            let x = s[0];
            x
          //^ &i32
        }
    """)

    fun testGenericOutput() = testExpr("""
        #[lang = "index"]
        pub trait Index<Idx: ?Sized> {
            type Output: ?Sized;
            fn index(&self, index: Idx) -> &Self::Output;
        }

        struct S<T>(T);

        impl<T> Index<i32> for S<T> {
            type Output = T;
            fn index(&self, index: i32) -> &T {
                unimplemented!()
            }
        }

        fn foo(s: S<i64>) {
            let x = s[0];
            x
          //^ &i64
        }
    """)

    fun testGenericIndexAndOutput() = testExpr("""
        #[lang = "index"]
        pub trait Index<Idx: ?Sized> {
            type Output: ?Sized;
            fn index(&self, index: Idx) -> &Self::Output;
        }

        struct Key<K>(K);
        struct S<K, V>(Key<K>, V);

        impl<K, V> Index<Key<K>> for S<K, V> {
            type Output = V;
            fn index(&self, index: Key<K>) -> &V {
                unimplemented!()
            }
        }

        fn foo(s: S<Key<i32>, f64>) {
            let x = s[Key(10)];
            x
          //^ &f64
        }
    """)

    fun testMultipleImplementation() = testExpr("""
        #[lang = "index"]
        pub trait Index<Idx: ?Sized> {
            type Output: ?Sized;
            fn index(&self, index: Idx) -> &Self::Output;
        }

        struct S;

        impl Index<f64> for S {
            type Output = f32;
            fn index(&self, index: f64) -> &f32 {
                unimplemented!()
            }
        }

        impl Index<i64> for S {
            type Output = i32;
            fn index(&self, index: i64) -> &i32 {
                unimplemented!()
            }
        }

        fn foo(s: S) {
            let x = s[0i64];
            x
          //^ &i32
        }
    """)

    fun testImplicitUsize() = testExpr("""
        #[lang = "index"]
        pub trait Index<Idx: ?Sized> {
            type Output: ?Sized;
            fn index(&self, index: Idx) -> &Self::Output;
        }

        struct Vec<T>;

        impl<T> Index<usize> for Vec<T> {
            type Output = T;
            fn index(&self, index: usize) -> &T {
                unimplemented!()
            }
        }

        fn foo(s: Vec<i32>) {
            let x = s[0];
            x
          //^ &i32
        }
    """)
}
