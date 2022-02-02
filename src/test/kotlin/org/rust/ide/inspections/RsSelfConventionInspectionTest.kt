/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.inspections

import org.rust.ProjectDescriptor
import org.rust.WithStdlibRustProjectDescriptor

class RsSelfConventionInspectionTest : RsInspectionsTestBase(RsSelfConventionInspection::class) {
    fun `test from`() = checkByText("""
        struct Foo;
        impl Foo {
            fn from_nothing(<warning descr="methods called `from_*` usually take no self; consider choosing a less ambiguous name">self</warning>) -> T { T() }
            fn from_ok(x: i32) -> T { T() }
        }
    """)

    fun `test into`() = checkByText("""
        struct Foo;
        impl Foo {
            fn into_u32(self) -> u32 { 0 }
            fn into_u32_mut(mut self) -> u32 { 0 }
            fn into_u16(<warning descr="methods called `into_*` usually take self by value; consider choosing a less ambiguous name">&self</warning>) -> u16 { 0 }
            fn <warning descr="methods called `into_*` usually take self by value; consider choosing a less ambiguous name">into_without_self</warning>() -> u16 { 0 }
        }
    """)

    fun `test to`() = checkByText("""
        struct Foo;
        impl Foo {
            fn to_a(self) -> u32 { 0 }
            fn to_b(&self) -> u32 { 92 }
            fn to_c(<warning descr="methods called `to_*` usually take self by reference or self by value; consider choosing a less ambiguous name">&mut self</warning>) -> u32 { 92 }
        }
    """)

    fun `test to mut`() = checkByText("""
        struct Foo;
        impl Foo {
            fn to_a_mut(<warning descr="methods called `to_*_mut` usually take self by mutable reference; consider choosing a less ambiguous name">self</warning>) -> u32 { 0 }
            fn to_b_mut(<warning descr="methods called `to_*_mut` usually take self by mutable reference; consider choosing a less ambiguous name">&self</warning>) -> u32 { 0 }
            fn to_c_mut(&mut self) -> u32 { 0 }
        }
    """)

    fun `test as`() = checkByText("""
        struct Foo;
        impl Foo {
            fn as_foo(<warning descr="methods called `as_*` usually take self by reference or self by mutable reference; consider choosing a less ambiguous name">self</warning>) -> u32 { 0 }
            fn as_bar(&self) -> u32 { 92 }
            fn as_baz(&mut self) -> u32 { 92 }
        }
    """)

    fun `test is`() = checkByText("""
        struct Foo;
        impl Foo {
            fn is_awesome(<warning descr="methods called `is_*` usually take self by reference or no self; consider choosing a less ambiguous name">self</warning>) {}
            fn is_awesome_ref(&self) {}
        }
    """)

    @ProjectDescriptor(WithStdlibRustProjectDescriptor::class)
    fun `test is suppressed for copyable`() = checkByText("""
        #[derive(Copy)]
        struct Copyable;
        impl Copyable {
            fn is_ok(self) {}
        }
    """)

    @ProjectDescriptor(WithStdlibRustProjectDescriptor::class)
    fun `test is suppressed for copyable on trait`() = checkByText("""
        use std::marker::Copy;
        trait Copyable: Copy {
            fn is_ok(self) {}
        }
    """)

    fun `test suppress to on trait impls but not on traits`() = checkByText("""
        struct Foo;
        trait Bar {
            fn to_something(<warning descr="methods called `to_*` usually take self by reference or self by value; consider choosing a less ambiguous name">&mut self</warning>) -> u32;
        }
        impl Bar for Foo {
            fn to_something(self) -> u32 { 0 }
        }
    """)

    fun `test arbitrary self type`() = checkByText("""
        #[lang="deref"]
        trait Deref {
            type Target;
        }

        struct Wrapper<T>(T);

        impl<T> Deref for Wrapper<T> {
            type Target = T;
        }

        struct Foo;
        impl Foo {
            fn as_foo(self: Wrapper<Self>) -> u32 { 0 }
            fn is_awesome(self: Wrapper<Self>) {}
            fn from_nothing(<warning descr="methods called `from_*` usually take no self; consider choosing a less ambiguous name">self: Wrapper<Self></warning>) -> u32 { 0 }
        }
    """)

    fun `test explicit self type`() = checkByText("""
        struct Foo;
        impl Foo {
            fn as_foo(<warning descr="methods called `as_*` usually take self by reference or self by mutable reference; consider choosing a less ambiguous name">self: Self</warning>) -> u32 { 0 }
            fn as_foo_2(self: &Self) -> u32 { 0 }
            fn as_foo_mut(self: &mut Self) -> u32 { 0 }
            fn is_awesome(self: &Self) {}
            fn into_foo(<warning descr="methods called `into_*` usually take self by value; consider choosing a less ambiguous name">self: &Self</warning>) -> u32 { 0 }
            fn into_foo_mut(<warning descr="methods called `into_*` usually take self by value; consider choosing a less ambiguous name">self: &mut Self</warning>) -> u32 { 0 }
            fn from_nothing(<warning descr="methods called `from_*` usually take no self; consider choosing a less ambiguous name">self: Self</warning>) -> u32 { 0 }
        }
    """)
}
