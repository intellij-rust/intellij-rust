/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.inspections

/**
 * Tests for Self Convention inspection
 */
class RsSelfConventionInspectionTest : RsInspectionsTestBase(RsSelfConventionInspection(), useStdLib = true) {

    fun testFrom() = checkByText("""
        struct Foo;
        impl Foo {
            fn from_nothing(<warning descr="methods called `from_*` usually take no self; consider choosing a less ambiguous name">self</warning>) -> T { T() }
            fn from_ok(x: i32) -> T { T() }
        }
    """)

    fun testInto() = checkByText("""
        struct Foo;
        impl Foo {
            fn into_u32(self) -> u32 { 0 }
            fn into_u32_mut(mut self) -> u32 { 0 }
            fn into_u16(<warning descr="methods called `into_*` usually take self by value; consider choosing a less ambiguous name">&self</warning>) -> u16 { 0 }
            fn <warning descr="methods called `into_*` usually take self by value; consider choosing a less ambiguous name">into_without_self</warning>() -> u16 { 0 }
        }
    """)

    fun testTo() = checkByText("""
        struct Foo;
        impl Foo {
            fn to_something(<warning descr="methods called `to_*` usually take self by reference; consider choosing a less ambiguous name">self</warning>) -> u32 { 0 }
            fn to_something_else(&self) -> u32 { 92 }
        }
    """)

    fun testIs() = checkByText("""
        struct Foo;
        impl Foo {
            fn is_awesome(<warning descr="methods called `is_*` usually take self by reference or no self; consider choosing a less ambiguous name">self</warning>) {}
            fn is_awesome_ref(&self) {}
        }
    """)

    fun `test is suppressed for copyable`() = checkByText("""
        #[derive(Copy)]
        struct Copyable;
        impl Copyable {
            fn is_ok(self) {}
        }
    """)

    fun `test is suppressed for copyable on trait`() = checkByText("""
        use std::marker::Copy;
        trait Copyable: Copy {
            fn is_ok(self) {}
        }
    """)

    fun `test suppress to on trait impls but not on traits`() = checkByText("""
        struct Foo;
        trait Bar {
            fn to_something(<warning descr="methods called `to_*` usually take self by reference; consider choosing a less ambiguous name">self</warning>) -> u32;
        }
        impl Bar for Foo {
            fn to_something(self) -> u32 { 0 }
        }
    """)
}
