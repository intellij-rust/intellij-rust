/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.intentions

class SubstituteAssociatedTypeIntentionTest : RsIntentionTestBase(SubstituteAssociatedTypeIntention()) {
    fun `test unavailable on type alias`() = doUnavailableTest("""
        type Alias = u32;
        fn foo() -> Alias/*caret*/ {
            unimplemented!()
        }
    """)

    fun `test unavailable on trait associated type`() = doUnavailableTest("""
        trait Trait {
            type Item;
        }
        fn foo<T: Trait>() -> T::/*caret*/Item {
            unimplemented!()
        }
    """)

    fun `test trait associated type with a default`() = doAvailableTest("""
        trait Trait {
            type Item = u32;
        }
        fn foo<T: Trait>() -> T::/*caret*/Item {
            unimplemented!()
        }
    """, """
        trait Trait {
            type Item = u32;
        }
        fn foo<T: Trait>() -> u32 {
            unimplemented!()
        }
    """)

    fun `test associated type in type context`() = doAvailableTest("""
        trait Trait {
            type Item;
            fn foo(&self) -> Self::Item;
        }
        impl Trait for () {
            type Item = i32;

            fn foo(&self) -> <Self as Trait>::/*caret*/Item {
                unimplemented!()
            }
        }
    """, """
        trait Trait {
            type Item;
            fn foo(&self) -> Self::Item;
        }
        impl Trait for () {
            type Item = i32;

            fn foo(&self) -> i32 {
                unimplemented!()
            }
        }
    """)

    fun `test associated type in type context with type parameters`() = doAvailableTest("""
        struct S<R>(R);
        trait Trait<T> {
            type Item;
            fn foo(&self, item: Self::Item) -> T;
        }
        impl<T> Trait<T> for () {
            type Item = S<T>;

            fn foo(&self, item: Self::/*caret*/Item) -> T {
                unimplemented!()
            }
        }
    """, """
        struct S<R>(R);
        trait Trait<T> {
            type Item;
            fn foo(&self, item: Self::Item) -> T;
        }
        impl<T> Trait<T> for () {
            type Item = S<T>;

            fn foo(&self, item: S<T>) -> T {
                unimplemented!()
            }
        }
    """)

    fun `test associated type in expression context`() = doAvailableTest("""
        struct S;
        impl S {
            fn bar() {}
        }
        trait Trait {
            type Item;
            fn foo(&self);
        }
        impl Trait for () {
            type Item = S;

            fn foo(&self) {
                <Self as Trait>::/*caret*/Item::bar();
            }
        }
    """, """
        struct S;
        impl S {
            fn bar() {}
        }
        trait Trait {
            type Item;
            fn foo(&self);
        }
        impl Trait for () {
            type Item = S;

            fn foo(&self) {
                S::bar();
            }
        }
    """)

    fun `test associated type in expression context with type parameters`() = doAvailableTest("""
        struct S<R>(R);
        impl<R> S<R> {
            fn bar() {}
        }
        trait Trait {
            type Item;
            fn foo(&self);
        }
        impl Trait for () {
            type Item = S<u32>;

            fn foo(&self) {
                <Self as Trait>::/*caret*/Item::bar();
            }
        }
    """, """
        struct S<R>(R);
        impl<R> S<R> {
            fn bar() {}
        }
        trait Trait {
            type Item;
            fn foo(&self);
        }
        impl Trait for () {
            type Item = S<u32>;

            fn foo(&self) {
                S::<u32>::bar();
            }
        }
    """)

    fun `test associated type in expression context with type qual`() = doAvailableTest("""
        struct S<R>(R);
        impl<R> S<R> {
            fn bar() {}
        }
        impl Trait for S<u32> {
            type Item = Self;
            fn foo(&self) {}
        }

        trait Trait {
            type Item;
            fn foo(&self);
        }
        impl Trait for () {
            type Item = <S<u32> as Trait>::Item;

            fn foo(&self) {
                <Self as Trait>::/*caret*/Item::bar();
            }
        }
    """, """
        struct S<R>(R);
        impl<R> S<R> {
            fn bar() {}
        }
        impl Trait for S<u32> {
            type Item = Self;
            fn foo(&self) {}
        }

        trait Trait {
            type Item;
            fn foo(&self);
        }
        impl Trait for () {
            type Item = <S<u32> as Trait>::Item;

            fn foo(&self) {
                S::<u32>::bar();
            }
        }
    """)

    fun `test import after substitution`() = doAvailableTest("""
        mod foo {
            pub struct S;
            pub trait Trait {
                type Item = S;
            }
        }
        fn foo<T: foo::Trait>() -> T::/*caret*/Item {
            unimplemented!()
        }
    """, """
        use foo::S;

        mod foo {
            pub struct S;
            pub trait Trait {
                type Item = S;
            }
        }
        fn foo<T: foo::Trait>() -> S {
            unimplemented!()
        }
    """)
}
