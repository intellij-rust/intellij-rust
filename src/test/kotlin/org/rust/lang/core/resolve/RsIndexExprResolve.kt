/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.resolve

class RsIndexExprResolve : RsResolveTestBase() {
    private val langItems: String = """
        #[lang = "index"]
        pub trait Index<Idx: ?Sized> {
            type Output: ?Sized;

            fn index(&self, index: Idx) -> &Self::Output;
        }

        #[lang = "index_mut"]
        pub trait IndexMut<Idx: ?Sized>: Index<Idx> {
            fn index_mut(&mut self, index: Idx) -> &mut Self::Output;
        }
    """.trimIndent()

    fun `test left bracket`() = checkByCode("""
        $langItems

        struct Foo(u32);

        impl Index<i32> for Foo {
            type Output = u32;

            fn index(&self, index: i32) -> &Self::Output { &self.0 }
             //X
        }

        fn foo(a: Foo) {
            let index: i32 = 0;
            let x: u32 = a[index];
                        //^
        }
    """)

    fun `test right bracket`() = checkByCode("""
        $langItems

        struct Foo(u32);

        impl Index<i32> for Foo {
            type Output = u32;

            fn index(&self, index: i32) -> &Self::Output { &self.0 }
             //X
        }

        fn foo(a: Foo) {
            let index: i32 = 0;
            let x: u32 = a[index];
                              //^
        }
    """)

    fun `test prefer IndexMut in assignment lhs`() = checkByCode("""
        $langItems

        struct Foo(u32);

        impl Index<i32> for Foo {
            type Output = u32;
            fn index(&self, index: i32) -> &Self::Output { &self.0 }
        }
        impl IndexMut<i32> for Foo {
            fn index_mut(&mut self, index: i32) -> &mut Self::Output { &mut self.0 }
             //X
        }

        fn foo(mut a: Foo) {
            a[0] = 0;
           //^
        }
    """)

    fun `test prefer Index in assignment rhs`() = checkByCode("""
        $langItems

        struct Foo(u32);

        impl Index<i32> for Foo {
            type Output = u32;
            fn index(&self, index: i32) -> &Self::Output { &self.0 }
             //X
        }
        impl IndexMut<i32> for Foo {
            fn index_mut(&mut self, index: i32) -> &mut Self::Output { &mut self.0 }
        }

        fn foo(a: Foo) {
            let b = a[0];
                   //^
        }
    """)
}
