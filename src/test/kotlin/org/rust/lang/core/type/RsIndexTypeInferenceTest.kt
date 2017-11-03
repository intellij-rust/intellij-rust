/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.type

import org.intellij.lang.annotations.Language

class RsIndexTypeInferenceTest : RsTypificationTestBase() {

    fun `test index`() = doTest("""
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
          //^ i32
        }
    """)

    fun `test generic output`() = doTest("""
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
          //^ i64
        }
    """)

    fun `test generic index and output`() = doTest("""
        struct Key<K>(K);
        struct S<K, V>(Key<K>, V);

        impl<K, V> Index<Key<K>> for S<K, V> {
            type Output = V;
            fn index(&self, index: Key<K>) -> &V {
                unimplemented!()
            }
        }

        fn foo(s: S<i32, f64>) {
            let x = s[Key(10)];
            x
          //^ f64
        }
    """)

    fun `test multiple implementation`() = doTest("""
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
          //^ i32
        }
    """)

    fun `test implicit usize`() = doTest("""
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
          //^ i32
        }
    """)

    fun `test dereference`() = doTest("""
        struct Vec<T>;

        impl<T> Index<usize> for Vec<T> {
            type Output = T;
            fn index(&self, index: usize) -> &T { unimplemented!() }
        }

        fn foo(v: Vec<u32>) -> u32 {
            let p = &v;
            let m = p[0];
            m
          //^ u32
        }
    """)

    fun `test transitive coercion`() = doTest("""
        #[lang = "deref"]
        pub trait Deref {
            type Target: ?Sized;
            fn deref(&self) -> &Self::Target;
        }

        struct A;
        struct B;

        impl Index<usize> for A {
            type Output = i32;
            fn index(&self, index: usize) -> &Self::Output { unimplemented!() }
        }

        impl Deref for B {
            type Target = A;
            fn deref(&self) -> &Self::Target { unimplemented!() }
        }

        fn main() {
            let b = B;
            let r = &b;
            let v = r[0];
            v;
          //^ i32
        }
    """)

    private fun doTest(@Language("Rust") code: String) {
        val indexLangItem = """
            #[lang = "index"]
            pub trait Index<Idx: ?Sized> {
                type Output: ?Sized;
                fn index(&self, index: Idx) -> &Self::Output;
            }
        """
        testExpr("$indexLangItem\n\n$code")
    }
}
