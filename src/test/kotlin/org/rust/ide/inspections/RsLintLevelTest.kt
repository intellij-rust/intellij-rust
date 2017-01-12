package org.rust.ide.inspections

/**
 * Tests for lint level detection.
 */
class RsLintLevelTest : RsInspectionsTestBase(true) {

    fun testDirrectAllow() = checkByText<RustStructNamingInspection>("""
        #[allow(non_camel_case_types)]
        struct foo;
    """)

    fun testDirrectWarn() = checkByText<RustStructNamingInspection>("""
        #[warn(non_camel_case_types)]
        struct <warning>foo</warning>;
    """)

    fun testDirrectDeny() = checkByText<RustStructNamingInspection>("""
        #[deny(non_camel_case_types)]
        struct <warning>foo</warning>;
    """)

    fun testParentAllow() = checkByText<RustFieldNamingInspection>("""
        #[allow(non_snake_case)]
        struct Foo {
            Bar: u32
        }
    """)

    fun testParentWarn() = checkByText<RustFieldNamingInspection>("""
        #[warn(non_snake_case)]
        struct Foo {
            <warning>Bar</warning>: u32
        }
    """)

    fun testParentDeny() = checkByText<RustFieldNamingInspection>("""
        #[deny(non_snake_case)]
        struct Foo {
            <warning>Bar</warning>: u32
        }
    """)

    fun testModuleOuterAllow() = checkByText<RustStructNamingInspection>("""
        #[allow(non_camel_case_types)]
        mod space {
            struct planet;
        }
    """)

    fun testModuleInnerAllow() = checkByText<RustStructNamingInspection>("""
        #![allow(non_camel_case_types)]
        struct planet;
    """)

    fun testGrandParentAllow() = checkByText<RustStructNamingInspection>("""
        #[allow(non_camel_case_types)]
        mod space {
            mod planet {
                struct inhabitant;
            }
        }
    """)

    fun testInnerTakesPrecedence() = checkByText<RustStructNamingInspection>("""
        #[warn(non_camel_case_types)]
        mod space {
            #![allow(non_camel_case_types)]
            struct planet;
        }
        #[allow(non_camel_case_types)]
        mod science {
            #![warn(non_camel_case_types)]
            struct <warning>section</warning>;
        }
    """)

    fun testIgnoresOtherItems() = checkByText<RustStructNamingInspection>("""
        #[allow(non_snake_case)]
        struct <warning>foo</warning>;
    """)

    fun testMultipleMetaItems() = checkByText<RustStructNamingInspection>("""
        #[allow(non_snake_case, non_camel_case_types, non_upper_case_globals)]
        struct foo;
    """)

    fun testMixedAttributes() = checkByText<RustStructNamingInspection>("""
        #[allow(non_camel_case_types, non_upper_case_globals)]
        mod space {
            #[allow(non_snake_case)]
            mod planet {
                #![warn(non_snake_case, non_upper_case_globals)]
                struct inhabitant;
            }
        }
    """)

    fun testWorksWithNonLevelAttributes() = checkByText<RustStructNamingInspection>("""
        #[allow(non_camel_case_types)]
        #[derive(Debug)]
        struct inhabitant;
    """)
}
