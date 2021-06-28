/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.inspections.lints

import org.rust.MockEdition
import org.rust.cargo.project.workspace.CargoWorkspace
import org.rust.ide.inspections.RsInspectionsTestBase

@MockEdition(CargoWorkspace.Edition.EDITION_2018)
class RsPrivateTypeLeakedInspectionTest : RsInspectionsTestBase(RsPrivateTypeLeakedInspection::class) {
    fun `test ignore leak of item that is not accessible`() = checkByText("""
        mod foo {
            mod bar {
                struct Bar;
            }

            pub fn foo(_: bar::Bar) {}
        }
    """)

    fun `test ignore leak in items that are not direct children of some mod`() = checkByText("""
        fn foo() {
            struct Bar;
            pub fn bar() -> Bar { Bar }
        }
    """)

    fun `test ignore private type usage inside function`() = checkByText("""
        mod foo {
            struct Bar;
            fn bar() {
                let _ = Bar;
            }
        }
    """)

    fun `test ignore leak in public field of private struct`() = checkByText("""
        mod foo {
            struct Baz;
            struct Bar {
                pub a: Baz
            }
        }
    """)

    fun `test ignore private type in field of public struct`() = checkByText("""
        mod foo {
            struct Baz;
            pub struct Bar {
                a: Baz
            }
        }
    """)

    fun `test leak in trait function`() = checkByText("""
        mod foo {
            struct Bar;

            pub trait Trait {
                fn foo() -> <warning descr="private type `Bar` leaked in public interface [E0446]">Bar</warning>;
            }
        }
    """)

    fun `test leak in struct named field`() = checkByText("""
        mod foo {
            struct Baz;
            pub struct Bar {
                pub a: <warning descr="private type `Baz` leaked in public interface [E0446]">Baz</warning>
            }
        }
    """)

    fun `test leak in struct tuple field`() = checkByText("""
        mod foo {
            struct Baz;
            pub struct Bar(pub <warning descr="private type `Baz` leaked in public interface [E0446]">Baz</warning>);
        }
    """)

    fun `test leak in enum named field`() = checkByText("""
        mod foo {
            struct Baz;

            pub enum E {
                Bar {
                    a: <warning descr="private type `Baz` leaked in public interface [E0446]">Baz</warning>
                }
            }
        }
    """)

    fun `test leak in enum tuple field`() = checkByText("""
        mod foo {
            struct Baz;
            pub struct Bar(pub <warning descr="private type `Baz` leaked in public interface [E0446]">Baz</warning>);
        }
    """)

    fun `test leak in function return type`() = checkByText("""
        mod foo {
            struct Bar;
            pub fn bar() -> <warning descr="private type `Bar` leaked in public interface [E0446]">Bar</warning> { Bar }
        }
    """)

    fun `test leak in function parameter`() = checkByText("""
        mod foo {
            struct Bar;
            pub fn bar(a: <warning descr="private type `Bar` leaked in public interface [E0446]">Bar</warning>) {}
        }
    """)

    fun `test leak in constant type`() = checkByText("""
        mod foo {
            struct Bar;
            pub const BAR: <warning descr="private type `Bar` leaked in public interface [E0446]">Bar</warning> = Bar;
        }
    """)

    fun `test private visibility`() = checkByText("""
        mod bar {
            struct Bar;
            pub fn foo(_: <warning descr="private type `Bar` leaked in public interface [E0446]">Bar</warning>) {}
        }
    """)

    fun `test crate restricted visibility`() = checkByText("""
        mod bar {
            pub(crate) struct Bar;
            pub fn foo(_: <warning descr="crate-restricted type `Bar` leaked in public interface [E0446]">Bar</warning>) {}
        }
    """)

    fun `test mod restricted visibility`() = checkByText("""
        mod bar {
            mod foo {
                pub(in super) struct Bar;
                pub fn foo(_: <warning descr="restricted type `Bar` leaked in public interface [E0446]">Bar</warning>) {}
            }
        }
    """)

    fun `test imported type`() = checkByText("""
        struct Bar(u32);

        mod foo {
            use crate::Bar;
            pub fn bar() -> <warning descr="private type `Bar` leaked in public interface [E0446]">Bar</warning> { Bar(0) }
        }
    """)

    fun `test leak in type alias`() = checkByText("""
        mod foo {
            struct Bar;
            pub type Foo = <warning descr="private type `Bar` leaked in public interface [E0446]">Bar</warning>;
        }
    """)

    fun `test leak in supertrait`() = checkByText("""
        mod foo {
            trait Bar {}
            pub trait Foo: <warning descr="private trait `Bar` leaked in public interface [E0445]">Bar</warning> {}
        }
    """)

    fun `test leak in type parameters`() = checkByText("""
        mod foo {
            trait Bar {}
            pub fn foo<T: <warning descr="private trait `Bar` leaked in public interface [E0445]">Bar</warning>>(_: T) {}
        }
    """)

    fun `test leak in where clause`() = checkByText("""
        mod foo {
            trait Bar {}
            pub fn foo<T>(_: T)
                where T: <warning descr="private trait `Bar` leaked in public interface [E0445]">Bar</warning>
            {}
        }
    """)

    fun `test leak in generic parameter`() = checkByText("""
        mod foo {
            struct Bar;

            pub struct S<T>(T);
            pub fn foo(_: S<<warning descr="private type `Bar` leaked in public interface [E0446]">Bar</warning>>) {}
        }
    """)

    fun `test multiple namespaces`() = checkByText("""
        mod foo {
            struct Bar {}
            const Bar: u32 = 0;

            pub fn foo() -> <warning descr="private type `Bar` leaked in public interface [E0446]">Bar</warning> { Bar {} }
        }
    """)

    fun `test allow soft error`() = checkByText("""
        #![allow(private_in_public)]

        mod foo {
            struct Bar;

            trait Foo {
                fn foo() -> Bar;
            }
        }
    """)

    fun `test allow hard error`() = checkByText("""
        #![allow(private_in_public)]

        mod foo {
            struct Bar;
            pub fn foo() -> Bar { Bar }
        }
    """)

    fun `test deny`() = checkByText("""
        #![deny(private_in_public)]

        mod foo {
            struct Bar;
            pub fn foo() -> <warning descr="private type `Bar` leaked in public interface [E0446]">Bar</warning> { Bar }
        }
    """)

    fun `test fix`() = checkFixByText("Make `Bar` public", """
        mod foo {
            struct Bar;
            pub fn foo() -> <warning descr="private type `Bar` leaked in public interface [E0446]">Bar/*caret*/</warning> { Bar }
        }
    """, """
        mod foo {
            pub struct Bar;
            pub fn foo() -> Bar { Bar }
        }
    """)
}
