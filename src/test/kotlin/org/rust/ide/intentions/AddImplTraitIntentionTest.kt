/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.intentions

class AddImplTraitIntentionTest : RsIntentionTestBase(AddImplTraitIntention::class) {
    fun `test empty trait`() = doAvailableTestWithLiveTemplate("""
        trait Foo {}

        struct S/*caret*/;
    """, "Foo\t\t", """
        trait Foo {}

        struct S;

        impl Foo/*caret*/ for S {}
    """)

    fun `test ignore method with default implementation`() = doAvailableTestWithLiveTemplate("""
        trait Foo {
            fn foo(&self) {}
        }

        struct S/*caret*/;
    """, "Foo\t\t", """
        trait Foo {
            fn foo(&self) {}
        }

        struct S;

        impl Foo/*caret*/ for S {}
    """)

    fun `test trait with method`() = doAvailableTestWithLiveTemplate("""
        trait Foo {
            fn foo(&self);
        }

        struct S/*caret*/;
    """, "Foo\t\t", """
        trait Foo {
            fn foo(&self);
        }

        struct S;

        impl Foo/*caret*/ for S {
            fn foo(&self) {
                todo!()
            }
        }
    """)

    fun `test trait with const`() = doAvailableTestWithLiveTemplate("""
        trait Foo {
            const FOO: u32;
        }

        struct S/*caret*/;
    """, "Foo\t\t", """
        trait Foo {
            const FOO: u32;
        }

        struct S;

        impl Foo/*caret*/ for S { const FOO: u32 = 0; }
    """)

    fun `test trait with associated type`() = doAvailableTestWithLiveTemplate("""
        trait Foo {
            type Type;
        }

        struct S/*caret*/;
    """, "Foo\t\t", """
        trait Foo {
            type Type;
        }

        struct S;

        impl Foo for S { type Type = (); }
    """)

    fun `test trait with multiple items`() = doAvailableTestWithLiveTemplate("""
        trait Foo {
            type Type;

            const FOO: u32;

            fn foo(&self);
        }

        struct S/*caret*/;
    """, "Foo\t\t", """
        trait Foo {
            type Type;

            const FOO: u32;

            fn foo(&self);
        }

        struct S;

        impl Foo/*caret*/ for S {
            type Type = ();
            const FOO: u32 = 0;

            fn foo(&self) {
                todo!()
            }
        }
    """)

    fun `test trait with method and 1 type parameter`() = doAvailableTestWithLiveTemplate("""
        trait Foo<T> {
            fn foo(&self) -> T;
        }

        struct S/*caret*/;
    """, "Foo\t\ti32\t", """
        trait Foo<T> {
            fn foo(&self) -> T;
        }

        struct S;

        impl Foo<i32/*caret*/> for S {
            fn foo(&self) -> i32 {
                todo!()
            }
        }
    """)

    fun `test trait with method and 2 type parameters`() = doAvailableTestWithLiveTemplate("""
        trait Foo<A, B> {
            fn foo(&self) -> (A, B);
        }

        struct S/*caret*/;
    """, "Foo\t\ti32\tu8\t", """
        trait Foo<A, B> {
            fn foo(&self) -> (A, B);
        }

        struct S;

        impl Foo<i32, u8/*caret*/> for S {
            fn foo(&self) -> (i32, u8) {
                todo!()
            }
        }
    """)

    fun `test generic struct`() = doAvailableTestWithLiveTemplate("""
        trait Foo {
            fn foo(&self) -> u32;
        }

        struct S<'a, R, T>(R, &'a T)/*caret*/;
    """, "Foo\t\t", """
        trait Foo {
            fn foo(&self) -> u32;
        }

        struct S<'a, R, T>(R, &'a T);

        impl<'a, R, T> Foo/*caret*/ for S<'a, R, T> {
            fn foo(&self) -> u32 {
                todo!()
            }
        }
    """)

    fun `test generic struct with where clause`() = doAvailableTestWithLiveTemplate("""
        trait Foo {
            fn foo(&self) -> u32;
        }

        struct S<'a, R, T>(R, &'a T) where R: Foo/*caret*/;
    """, "Foo\t\t", """
        trait Foo {
            fn foo(&self) -> u32;
        }

        struct S<'a, R, T>(R, &'a T) where R: Foo;

        impl<'a, R, T> Foo/*caret*/ for S<'a, R, T> where R: Foo {
            fn foo(&self) -> u32 {
                todo!()
            }
        }
    """)

    fun `test import trait from module`() = doAvailableTestWithLiveTemplate("""
        mod foo {
            pub trait Foo {
                fn foo(&self) -> u32;
            }
        }

        struct S/*caret*/;
    """, "Foo\t\t", """
        use crate::foo::Foo;

        mod foo {
            pub trait Foo {
                fn foo(&self) -> u32;
            }
        }

        struct S;

        impl Foo/*caret*/ for S {
            fn foo(&self) -> u32 {
                todo!()
            }
        }
    """)

    fun `test invalid trait name`() = doAvailableTestWithLiveTemplate("""
        struct Foo;

        struct S/*caret*/;
    """, "Foo\t", """
        struct Foo;

        struct S;

        impl Foo/*caret*/ for S {}
    """)
}
