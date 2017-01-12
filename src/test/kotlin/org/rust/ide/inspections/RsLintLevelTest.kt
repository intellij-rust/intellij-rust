package org.rust.ide.inspections

/**
 * Tests for lint level detection.
 */
class RsLintLevelTest : RsInspectionsTestBase(true) {

    fun testDirrectAllow() = checkByText<RsStructNamingInspection>("""
        #[allow(non_camel_case_types)]
        struct foo;
    """)

    fun testDirrectWarn() = checkByText<RsStructNamingInspection>("""
        #[warn(non_camel_case_types)]
        struct <warning>foo</warning>;
    """)

    fun testDirrectDeny() = checkByText<RsStructNamingInspection>("""
        #[deny(non_camel_case_types)]
        struct <warning>foo</warning>;
    """)

    fun testParentAllow() = checkByText<RsFieldNamingInspection>("""
        #[allow(non_snake_case)]
        struct Foo {
            Bar: u32
        }
    """)

    fun testParentWarn() = checkByText<RsFieldNamingInspection>("""
        #[warn(non_snake_case)]
        struct Foo {
            <warning>Bar</warning>: u32
        }
    """)

    fun testParentDeny() = checkByText<RsFieldNamingInspection>("""
        #[deny(non_snake_case)]
        struct Foo {
            <warning>Bar</warning>: u32
        }
    """)

    fun testModuleOuterAllow() = checkByText<RsStructNamingInspection>("""
        #[allow(non_camel_case_types)]
        mod space {
            struct planet;
        }
    """)

    fun testModuleInnerAllow() = checkByText<RsStructNamingInspection>("""
        #![allow(non_camel_case_types)]
        struct planet;
    """)

    fun testGrandParentAllow() = checkByText<RsStructNamingInspection>("""
        #[allow(non_camel_case_types)]
        mod space {
            mod planet {
                struct inhabitant;
            }
        }
    """)

    fun testInnerTakesPrecedence() = checkByText<RsStructNamingInspection>("""
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

    fun testIgnoresOtherItems() = checkByText<RsStructNamingInspection>("""
        #[allow(non_snake_case)]
        struct <warning>foo</warning>;
    """)

    fun testMultipleMetaItems() = checkByText<RsStructNamingInspection>("""
        #[allow(non_snake_case, non_camel_case_types, non_upper_case_globals)]
        struct foo;
    """)

    fun testMixedAttributes() = checkByText<RsStructNamingInspection>("""
        #[allow(non_camel_case_types, non_upper_case_globals)]
        mod space {
            #[allow(non_snake_case)]
            mod planet {
                #![warn(non_snake_case, non_upper_case_globals)]
                struct inhabitant;
            }
        }
    """)

    fun testWorksWithNonLevelAttributes() = checkByText<RsStructNamingInspection>("""
        #[allow(non_camel_case_types)]
        #[derive(Debug)]
        struct inhabitant;
    """)
}
