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
        impl S {
            //noinspection RustSelfConvention
            fn is_foo(self) { }
            fn is_bar(<warning>s<caret>elf</warning>) { }
        }

        struct T;
        //noinspection RustSelfConvention
        impl T {
            fn is_foo(self) { }
            fn is_bar(self) { }
        }
    """)
}
