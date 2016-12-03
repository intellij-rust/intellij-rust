package org.rust.ide.inspections

/**
 * Tests for Self Convention inspection
 */
class RustSelfConventionInspectionTest: RustInspectionsTestBase() {

    fun testFrom() = checkByText<RustSelfConventionInspection>("""
        struct Foo;
        impl Foo {
            fn from_nothing(<warning descr="methods called `from_*` usually take no self; consider choosing a less ambiguous name">self</warning>) -> T { T() }
        }
    """)

    fun testInto() = checkByText<RustSelfConventionInspection>("""
        struct Foo;
        impl Foo {
            fn into_u32(self) -> u32 { 0 }
            fn into_u16(<warning descr="methods called `into_*` usually take self by value; consider choosing a less ambiguous name">&self</warning>) -> u16 { 0 }
            fn <warning descr="methods called `into_*` usually take self by value; consider choosing a less ambiguous name">into_without_self</warning>() -> u16 { 0 }
        }
    """)

    fun testTo() = checkByText<RustSelfConventionInspection>("""
        struct Foo;
        impl Foo {
            fn to_something(<warning descr="methods called `to_*` usually take self by reference; consider choosing a less ambiguous name">self</warning>) -> u32 { 0 }
        }
    """)

    fun testIs() = checkByText<RustSelfConventionInspection>("""
        struct Foo;
        impl Foo {
            fn is_awesome(<warning descr="methods called `is_*` usually take self by reference or no self; consider choosing a less ambiguous name">self</warning>) {}
        }
    """)

    fun testIsSuppresedForCopyable() = checkByText<RustSelfConventionInspection>("""
        #[derive(Copy)]
        struct Copyable;
        impl Copyable {
            fn is_ok(self) {}
        }
    """)
}
