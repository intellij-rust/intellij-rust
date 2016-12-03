package org.rust.ide.inspections

/**
 * Tests for inspections suppression
 */
class RustInspectionSuppressorTest : RustInspectionsTestBase() {

    fun testWithoutSuppression() = checkByText<RustSelfConventionInspection>("""
        struct S;
        impl S {
            fn is_foo(<warning>s<caret>elf</warning>) { }
        }
    """)

    fun testSuppression() = checkByText<RustSelfConventionInspection>("""
        struct S;
        //noinspection RustSelfConvention
        impl S {
            fn is_foo(self) { }
        }
    """)
}
