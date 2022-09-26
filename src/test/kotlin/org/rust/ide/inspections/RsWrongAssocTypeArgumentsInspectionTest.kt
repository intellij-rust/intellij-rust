/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.inspections

import org.rust.ProjectDescriptor
import org.rust.WithStdlibRustProjectDescriptor

class RsWrongAssocTypeArgumentsInspectionTest : RsInspectionsTestBase(RsWrongAssocTypeArgumentsInspection::class) {
    fun `test valid associated type`() = checkByText("""
        trait Foo {
            type Bar;
        }
        fn foo(_: &dyn Foo<Bar=u32>) {}
    """)

    fun `test valid supertrait associated type`() = checkByText("""
        trait A {
            type A;
        }
        trait B: A {}
        fn foo(_: &dyn B<A=u32>) {}
    """)

    fun `test do not check missing types in supertrait`() = checkByText("""
        trait A {
            type A;
        }
        trait B: A {}
    """)

    fun `test do not check missing types in generic bound`() = checkByText("""
        trait Foo {
            type A;
        }
        fn foo<T: Foo>(t: T) {}
    """)

    fun `test E0191 missing associated type`() = checkByText("""
        trait Trait {
            type A;
        }
        fn foo(_: &dyn <error descr="The value of the associated type `A` (from trait `Trait`) must be specified [E0191]">Trait</error>) {}
    """)

    fun `test E0191 missing associated type from supertrait`() = checkByText("""
        trait A {
            type A;
        }
        trait B: A {}
        fn foo(_: &dyn <error descr="The value of the associated type `A` (from trait `A`) must be specified [E0191]">B</error>) {}
    """)

    fun `test E0191 multiple missing types`() = checkByText("""
        trait A {
            type A;
        }
        trait B: A {
            type B;
        }
        fn foo(_: &dyn <error descr="The value of the associated types `A` (from trait `A`), `B` (from trait `B`) must be specified [E0191]">B</error>) {}
    """)

    fun `test fix E0191 type without generic arguments`() = checkFixByText("Add missing associated types", """
        trait Foo {
            type A;
        }
        fn foo(_: &dyn <error descr="The value of the associated type `A` (from trait `Foo`) must be specified [E0191]">Foo/*caret*/</error>) {}
    """, """
        trait Foo {
            type A;
        }
        fn foo(_: &dyn Foo<A=()>) {}
    """)

    fun `test fix E0191 type with generic arguments`() = checkFixByText("Add missing associated types", """
        trait Foo<'a, T> {
            type A;
            fn test(&self) -> &'a T;
        }
        fn foo<'a>(_: &dyn <error descr="The value of the associated type `A` (from trait `Foo`) must be specified [E0191]">Foo<'a, u32>/*caret*/</error>) {}
    """, """
        trait Foo<'a, T> {
            type A;
            fn test(&self) -> &'a T;
        }
        fn foo<'a>(_: &dyn Foo<'a, u32, A=()>) {}
    """)

    fun `test fix E0191 type with associated type arguments`() = checkFixByText("Add missing associated types", """
        trait Foo<'a, T> {
            type A;
            type B;
            fn test(&self) -> &'a T;
        }
        fn foo<'a>(_: &dyn <error descr="The value of the associated type `B` (from trait `Foo`) must be specified [E0191]">Foo<'a, u32, A=u32>/*caret*/</error>) {}
    """, """
        trait Foo<'a, T> {
            type A;
            type B;
            fn test(&self) -> &'a T;
        }
        fn foo<'a>(_: &dyn Foo<'a, u32, A=u32, B=()>) {}
    """)

    fun `test fix E0191 add types from supertraits`() = checkFixByText("Add missing associated types", """
        trait A {
            type A;
        }
        trait B: A {
            type B;
        }
        fn foo(_: &dyn <error descr="The value of the associated types `A` (from trait `A`), `B` (from trait `B`) must be specified [E0191]">B<>/*caret*/</error>) {}
    """, """
        trait A {
            type A;
        }
        trait B: A {
            type B;
        }
        fn foo(_: &dyn B<A=(), B=()>) {}
    """)

    fun `test E0220 redundant type`() = checkByText("""
        trait Trait {}
        fn foo(_: &dyn Trait<<error descr="Associated type `A` not found for `Trait` [E0220]">A=u32</error>>) {}
    """)

    fun `test E0220 redundant type in supertrait`() = checkByText("""
        trait Trait {}
        trait Trait2: Trait<<error descr="Associated type `A` not found for `Trait` [E0220]">A=u32</error>> {}
    """)

    fun `test E0220 redundant type in generic bound`() = checkByText("""
        trait Trait {}
        fn foo<T: Trait<<error descr="Associated type `A` not found for `Trait` [E0220]">A=u32</error>>>(t: T) {}
    """)

    fun `test fix E0220 redundant type`() = checkFixByText("Remove redundant associated type", """
        trait Trait {}
        fn foo(_: &dyn Trait<<error descr="Associated type `A` not found for `Trait` [E0220]">A=u32/*caret*/</error>>) {}
    """, """
        trait Trait {}
        fn foo(_: &dyn Trait) {}
    """)

    fun `test fix E0220 redundant type remove comma after`() = checkFixByText("Remove redundant associated type", """
        trait Trait {
            type Foo;
        }
        fn foo(_: &dyn Trait<<error descr="Associated type `A` not found for `Trait` [E0220]">A=u32/*caret*/</error>, Foo=u32>) {}
    """, """
        trait Trait {
            type Foo;
        }
        fn foo(_: &dyn Trait<Foo=u32>) {}
    """)

    fun `test fix E0220 impl trait target trait`() = checkFixByText("Remove redundant associated type", """
        trait Trait {}
        trait Trait2 {}
        impl Trait2 for Trait<<error descr="Associated type `A` not found for `Trait` [E0220]">A=u32/*caret*/</error>> {}
    """, """
        trait Trait {}
        trait Trait2 {}
        impl Trait2 for Trait {}
    """)

    fun `test fix E0220 redundant type base type`() = checkFixByText("Remove redundant associated type", """
        trait Trait {}
        fn foo() {
            let x: Trait<<error descr="Associated type `A` not found for `Trait` [E0220]">A=u32/*caret*/</error>>;
        }
    """, """
        trait Trait {}
        fn foo() {
            let x: Trait;
        }
    """)

    fun `test impl trait implemented trait`() = checkByText("""
        trait Trait {
            type Foo;
        }
        impl Trait for () {
            type Foo = u32;
        }
    """)

    fun `test E0191 impl trait target trait`() = checkByText("""
        trait Trait {
            type Foo;
        }
        trait Trait2 {}
        impl Trait2 for <error descr="The value of the associated type `Foo` (from trait `Trait`) must be specified [E0191]">Trait</error> {}
    """)

    fun `test E0191 trait type without dyn`() = checkByText("""
        trait Trait {
            type Foo;
        }
        fn foo() {
            let x: <error descr="The value of the associated type `Foo` (from trait `Trait`) must be specified [E0191]">Trait</error>;
        }
    """)

    @ProjectDescriptor(WithStdlibRustProjectDescriptor::class)
    fun `test fn-like traits`() = checkByText("""
        fn foo1(_: &mut dyn FnOnce(&mut [u8]) -> usize) {}
        fn foo2(_: &mut dyn FnMut(&mut [u8]) -> usize) {}
        fn foo3(_: &mut dyn Fn(&mut [u8]) -> usize) {}
    """)

    fun `test self`() = checkByText("""
        trait Trait {
            type FOO;

            fn foo() -> &'static Self {
                unreachable!()
            }
        }

        fn foo1(_: &mut dyn FnOnce(&mut [u8]) -> usize) {}
        fn foo2(_: &mut dyn FnMut(&mut [u8]) -> usize) {}
        fn foo3(_: &mut dyn Fn(&mut [u8]) -> usize) {}
    """)

    fun `test impl argument`() = checkByText("""
        trait Trait {
            type FOO;
        }

        fn foo(_: impl Trait) {}
    """)

    fun `test impl return type`() = checkByText("""
        trait Trait {
            type FOO;
        }

        impl Trait for () {
            type FOO = ();
        }

        fn foo() -> impl Trait {}
    """)

    fun `test type qual`() = checkByText("""
        trait Trait {
            type FOO;
        }
        trait Trait2 {}

        fn foo<T: Trait>(t: T) where <T as Trait>::FOO: Trait2 {}
    """)

    fun `test associated type default`() = checkByText("""
        #![feature(associated_type_defaults)]

        trait A {
            type X = i32;
        }
        fn func(_: &dyn <error descr="The value of the associated type `X` (from trait `A`) must be specified [E0191]">A</error>) {}
    """)
}
