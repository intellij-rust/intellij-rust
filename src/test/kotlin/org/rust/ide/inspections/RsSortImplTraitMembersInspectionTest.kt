/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.inspections

class RsSortImplTraitMembersInspectionTest : RsInspectionsTestBase(RsSortImplTraitMembersInspection()) {

    fun `test same order`() = checkFixIsUnavailable("Apply same member order", """
        struct Struct {
            i: i32
        }

        struct T;

        trait Trait {
            const ID1: i32;
            const ID2: i32;
            type T1;
            type T2;
            fn test1(&self) -> i32;
            fn test2(&self) -> i32;
            fn test3(&self) -> i32;
        }

        /*caret*/impl Trait for Struct {
            const ID1: i32 = 1;
            const ID2: i32 = 2;
            type T1 = T;
            type T2 = T;
            fn test1(&self) -> i32 {
                self.i
            }
            fn test2(&self) -> i32 {
                self.i * 2
            }
            fn test3(&self) -> i32 {
                self.i * 3
            }
        }
    """)

    fun `test empty`() = checkFixIsUnavailable("Apply same member order", """
        trait Foo {
        }

        /*caret*/impl Foo for () {
        }
    """)

    fun `test empty impl`() = checkFixIsUnavailable("Apply same member order", """
        trait Foo {
            type x;
        }

        <error descr="Not all trait items implemented, missing: `x` [E0046]">/*caret*/impl Foo for ()</error> {
        }
    """)

    fun `test empty trait`() = checkFixIsUnavailable("Apply same member order", """
        trait Foo {
        }

        /*caret*/impl Foo for () {
            type <error descr="Method `x` is not a member of trait `Foo` [E0407]">x</error> = ();
        }
    """)


    fun `test different impl`() = checkFixIsUnavailable("Apply same member order", """
        trait Foo {
            type x;
        }
        <error descr="Not all trait items implemented, missing: `x` [E0046]">/*caret*/impl Foo for ()</error> {
            type <error descr="Method `y` is not a member of trait `Foo` [E0407]">y</error> = ();
        }
    """)

    fun `test different order`() = checkFixByText("Apply same member order", """
        struct Struct {
            i: i32
        }

        struct T;

        trait Trait {
            const ID1: i32;
            const ID2: i32;
            type T1;
            type T2;
            fn test1(&self) -> i32;
            fn test2(&self) -> i32;
            fn test3(&self) -> i32;
        }

        <weak_warning descr="Different impl member order from the trait">/*caret*/impl Trait for Struct</weak_warning> {
            type T2 = T;
            const ID2: i32 = 2;
            fn test3(&self) -> i32 {
                self.i * 3
            }
            fn test1(&self) -> i32 {
                self.i
            }
            fn test2(&self) -> i32 {
                self.i * 2
            }
            type T1 = T;
            const ID1: i32 = 1;
        }
    """, """
        struct Struct {
            i: i32
        }

        struct T;

        trait Trait {
            const ID1: i32;
            const ID2: i32;
            type T1;
            type T2;
            fn test1(&self) -> i32;
            fn test2(&self) -> i32;
            fn test3(&self) -> i32;
        }

        /*caret*/impl Trait for Struct {
            const ID1: i32 = 1;
            const ID2: i32 = 2;
            type T1 = T;
            type T2 = T;
            fn test1(&self) -> i32 {
                self.i
            }
            fn test2(&self) -> i32 {
                self.i * 2
            }
            fn test3(&self) -> i32 {
                self.i * 3
            }
        }
    """)


    fun `test different order with same name`() = checkFixByText("Apply same member order", """
        trait Foo {
            type bar;
            fn bar();
        }

        <weak_warning descr="Different impl member order from the trait">/*caret*/impl Foo for ()</weak_warning> {
            fn bar() {
            }
            type bar = ();
        }
    """, """
        trait Foo {
            type bar;
            fn bar();
        }

        impl Foo for () {
            type bar = ();
            fn bar() {
            }
        }
    """)

    fun `test different order with different files`() = checkFixByFileTree("Apply same member order", """
        //- foo.rs
        pub trait Trait {
            const ID1: i32;
            const ID2: i32;
            type T1;
            type T2;
            fn test1(&self) -> i32;
            fn test2(&self) -> i32;
            fn test3(&self) -> i32;
        }

        //- main.rs
        mod foo;

        use foo::Trait;

        struct Struct {
            i: i32
        }

        struct T;

        <weak_warning descr="Different impl member order from the trait">/*caret*/impl Trait for Struct</weak_warning> {
            type T2 = T;
            const ID2: i32 = 2;
            fn test3(&self) -> i32 {
                self.i * 3
            }
            fn test1(&self) -> i32 {
                self.i
            }
            fn test2(&self) -> i32 {
                self.i * 2
            }
            type T1 = T;
            const ID1: i32 = 1;
        }
    """, """
        mod foo;

        use foo::Trait;

        struct Struct {
            i: i32
        }

        struct T;

        /*caret*/impl Trait for Struct {
            const ID1: i32 = 1;
            const ID2: i32 = 2;
            type T1 = T;
            type T2 = T;
            fn test1(&self) -> i32 {
                self.i
            }
            fn test2(&self) -> i32 {
                self.i * 2
            }
            fn test3(&self) -> i32 {
                self.i * 3
            }
        }
    """)
}
