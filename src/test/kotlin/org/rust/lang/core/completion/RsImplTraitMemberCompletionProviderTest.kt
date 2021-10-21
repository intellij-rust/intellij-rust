/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.completion


class RsImplTraitMemberCompletionProviderTest : RsCompletionTestBase() {
    fun `test constant`() = doFirstCompletion("""
        trait Foo {
            const FOO: u32;
        }

        impl Foo for () {
            /*caret*/
        }
    """, """
        trait Foo {
            const FOO: u32;
        }

        impl Foo for () {
            const FOO: u32 = 0/*caret*/;
        }
    """)

    fun `test constant type substitution`() = doFirstCompletion("""
        struct S<R>(R);

        trait Foo<T> {
            const FOO: S<T>;
        }

        impl<X> Foo<X> for () {
            /*caret*/
        }
    """, """
        struct S<R>(R);

        trait Foo<T> {
            const FOO: S<T>;
        }

        impl<X> Foo<X> for () {
            const FOO: S<X> = S(())/*caret*/;
        }
    """)

    fun `test type alias`() = doFirstCompletion("""
        trait Foo {
            type FOO;
        }

        impl Foo for () {
            /*caret*/
        }
    """, """
        trait Foo {
            type FOO;
        }

        impl Foo for () {
            type FOO = ()/*caret*/;
        }
    """)

    fun `test function`() = doFirstCompletion("""
        trait Foo {
            fn foo(&self, a: u32, b: u32) -> u32;
        }

        impl Foo for () {
            /*caret*/
        }
    """, """
        trait Foo {
            fn foo(&self, a: u32, b: u32) -> u32;
        }

        impl Foo for () {
            fn foo(&self, a: u32, b: u32) -> u32 {
                todo!()/*caret*/
            }
        }
    """)

    fun `test multiple constants`() = checkContainsCompletion(
        listOf("const FOO: u32 = 0;", "const BAR: u32 = 0;", "const BAZ: u32 = 0;"), """
        trait Foo {
            const FOO: u32;
            const BAR: u32;
            const BAZ: u32;
        }

        impl Foo for () {
            /*caret*/
        }
    """)

    fun `test multiple types`() = checkContainsCompletion(
        listOf("type FOO = ();", "type BAR = ();", "type BAZ = ();"), """
        trait Foo {
            type FOO;
            type BAR;
            type BAZ;
        }

        impl Foo for () {
            /*caret*/
        }
    """)

    fun `test multiple functions`() = checkContainsCompletionPrefixes(
        listOf("fn foo", "fn bar", "fn baz"), """
        trait Foo {
            fn foo();
            fn bar();
            fn baz();
        }

        impl Foo for () {
            /*caret*/
        }
    """)

    fun `test ignore existing constants`() = checkNotContainsCompletion("const FOO", """
        trait Foo {
            const FOO: u32;
            const BAR: u32;
            const BAZ: u32;
        }

        impl Foo for () {
            const FOO: u32 = 0;
            /*caret*/
        }
    """)

    fun `test ignore existing types`() = checkNotContainsCompletion("type FOO", """
        trait Foo {
            type FOO;
            type BAR;
            type BAZ;
        }

        impl Foo for () {
            type FOO = ();
            /*caret*/
        }
    """)

    fun `test ignore existing functions`() = checkNotContainsCompletion("fn foo", """
        trait Foo {
            fn foo();
            fn bar();
            fn baz();
        }

        impl Foo for () {
            fn foo() {}
            /*caret*/
        }
    """)

    fun `test offer item with different type but same name`() = checkContainsCompletionPrefixes(listOf("fn FOO"), """
        trait Foo {
            type FOO: u32;
            fn FOO();
        }

        impl Foo for () {
            type FOO = ();
            /*caret*/
        }
    """)

    fun `test auto import constant type`() = doFirstCompletion("""
        mod foo {
            pub struct S;

            pub trait Foo {
                const FOO: S;
            }
        }

        impl foo::Foo for () {
            /*caret*/
        }
    """, """
        use foo::S;

        mod foo {
            pub struct S;

            pub trait Foo {
                const FOO: S;
            }
        }

        impl foo::Foo for () {
            const FOO: S = S/*caret*/;
        }
    """)

    fun `test auto import constant type with alias`() = doFirstCompletion("""
        mod foo {
            pub type ALIAS = u32;

            pub trait Foo {
                const FOO: ALIAS;
            }
        }

        impl foo::Foo for () {
            /*caret*/
        }
    """, """
        use foo::ALIAS;

        mod foo {
            pub type ALIAS = u32;

            pub trait Foo {
                const FOO: ALIAS;
            }
        }

        impl foo::Foo for () {
            const FOO: ALIAS = 0/*caret*/;
        }
    """)

    fun `test auto import function types`() = doFirstCompletion("""
        mod foo {
            pub struct S;
            pub struct T;

            pub trait Foo {
                fn foo(s: S) -> T;
            }
        }

        impl foo::Foo for () {
            /*caret*/
        }
    """, """
        use foo::{S, T};

        mod foo {
            pub struct S;
            pub struct T;

            pub trait Foo {
                fn foo(s: S) -> T;
            }
        }

        impl foo::Foo for () {
            fn foo(s: S) -> T {
                todo!()/*caret*/
            }
        }
    """)

    fun `test auto import function types with aliases`() = doFirstCompletion("""
        mod foo {
            pub type ALIAS1 = u32;
            pub type ALIAS2 = usize;

            pub trait Foo {
                fn foo(s: ALIAS1) -> ALIAS2;
            }
        }

        impl foo::Foo for () {
            /*caret*/
        }
    """, """
        use foo::{ALIAS1, ALIAS2};

        mod foo {
            pub type ALIAS1 = u32;
            pub type ALIAS2 = usize;

            pub trait Foo {
                fn foo(s: ALIAS1) -> ALIAS2;
            }
        }

        impl foo::Foo for () {
            fn foo(s: ALIAS1) -> ALIAS2 {
                todo!()/*caret*/
            }
        }
    """)

    fun `test complete full function signature`() = doFirstCompletion("""
        trait T1 {}
        trait T2 {
            fn foo<T>() -> T where T: T1;
        }

        impl T2 for () {
            /*caret*/
        }
    """, """
        trait T1 {}
        trait T2 {
            fn foo<T>() -> T where T: T1;
        }

        impl T2 for () {
            fn foo<T>() -> T where T: T1 {
                todo!()/*caret*/
            }
        }
    """)

    fun `test substitute generic types in function`() = doFirstCompletion("""
        trait T2<T> {
            fn foo() -> T;
        }

        impl T2<u32> for () {
            /*caret*/
        }
    """, """
        trait T2<T> {
            fn foo() -> T;
        }

        impl T2<u32> for () {
            fn foo() -> u32 {
                todo!()/*caret*/
            }
        }
    """)

    fun `test substitute generic types with alias in function`() = doFirstCompletion("""
        trait T2<T> {
            fn foo() -> T;
        }

        type Alias = u32;

        impl T2<Alias> for () {
            /*caret*/
        }
    """, """
        trait T2<T> {
            fn foo() -> T;
        }

        type Alias = u32;

        impl T2<Alias> for () {
            fn foo() -> Alias {
                todo!()/*caret*/
            }
        }
    """)

    fun `test substitute generic types with alias in constant`() = doFirstCompletion("""
        trait T2<T> {
            const FOO: T;
        }

        type Alias = u32;

        impl T2<Alias> for () {
            /*caret*/
        }
    """, """
        trait T2<T> {
            const FOO: T;
        }

        type Alias = u32;

        impl T2<Alias> for () {
            const FOO: Alias = 0/*caret*/;
        }
    """)

    fun `test complete after fn keyword`() = doFirstCompletion("""
        trait Trait {
            fn foo(&self);
        }

        impl Trait for () {
            fn /*caret*/
        }
    """, """
        trait Trait {
            fn foo(&self);
        }

        impl Trait for () {
            fn foo(&self) {
                todo!()/*caret*/
            }
        }
    """)

    fun `test complete in function name`() = doFirstCompletion("""
        trait Trait {
            fn foo(&self);
        }

        impl Trait for () {
            fn fo/*caret*/
        }
    """, """
        trait Trait {
            fn foo(&self);
        }

        impl Trait for () {
            fn foo(&self) {
                todo!()/*caret*/
            }
        }
    """)

    fun `test complete after const keyword`() = doFirstCompletion("""
        trait Trait {
            const FOO: u32;
        }

        impl Trait for () {
            const /*caret*/
        }
    """, """
        trait Trait {
            const FOO: u32;
        }

        impl Trait for () {
            const FOO: u32 = 0/*caret*/;
        }
    """)

    fun `test complete in constant name`() = doFirstCompletion("""
        trait Trait {
            const FOO: u32;
        }

        impl Trait for () {
            const FO/*caret*/
        }
    """, """
        trait Trait {
            const FOO: u32;
        }

        impl Trait for () {
            const FOO: u32 = 0/*caret*/;
        }
    """)

    fun `test complete after type keyword`() = doFirstCompletion("""
        trait Trait {
            type FOO;
        }

        impl Trait for () {
            type /*caret*/
        }
    """, """
        trait Trait {
            type FOO;
        }

        impl Trait for () {
            type FOO = ()/*caret*/;
        }
    """)

    fun `test complete in type name`() = doFirstCompletion("""
        trait Trait {
            type FOO;
        }

        impl Trait for () {
            type FO/*caret*/
        }
    """, """
        trait Trait {
            type FOO;
        }

        impl Trait for () {
            type FOO = ()/*caret*/;
        }
    """)

    fun `test filter items after keyword 1`() = checkNoCompletion("""
        trait Trait {
            fn foo(&self);
            type BAR;
        }

        impl Trait for () {
            fn BA/*caret*/
        }
    """)

    fun `test filter items after keyword 2`() = checkNoCompletion("""
        trait Trait {
            fn foo(&self);
            type BAR;
        }

        impl Trait for () {
            fn ty/*caret*/
        }
    """)

    fun `test nested function`() = doSingleCompletion("""
        fn foo() {
            trait Trait {
                fn foo(&self);
            }

            impl Trait for () {
                fn fo/*caret*/
            }
        }
    """, """
        fn foo() {
            trait Trait {
                fn foo(&self);
            }

            impl Trait for () {
                fn foo(&self) {
                    todo!()/*caret*/
                }
            }
        }
    """)

    fun `test nested constant`() = doSingleCompletion("""
        fn foo() {
            struct S {
                attribute1: u32,
                attribute2: u32,
                attribute3: u32,
            }

            trait Trait {
                const FOO: S;
            }

            impl Trait for () {
                const FO/*caret*/
            }
        }
    """, """
        fn foo() {
            struct S {
                attribute1: u32,
                attribute2: u32,
                attribute3: u32,
            }

            trait Trait {
                const FOO: S;
            }

            impl Trait for () {
                const FOO: S = S {
                    attribute1: 0,
                    attribute2: 0,
                    attribute3: 0,
                }/*caret*/;
            }
        }
    """)
}
