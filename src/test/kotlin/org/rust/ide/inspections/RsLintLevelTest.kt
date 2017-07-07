/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.inspections

/**
 * Tests for lint level detection.
 */
class RsLintLevelStructTest : RsInspectionsTestBase(RsStructNamingInspection(), true) {

    fun testDirrectAllow() = checkByText("""
        #[allow(non_camel_case_types)]
        struct foo;
    """)

    fun testDirrectAllowBadStyle() = checkByText("""
        #[allow(bad_style)]
        struct foo;
    """)

    fun testDirrectWarn() = checkByText("""
        #[warn(non_camel_case_types)]
        struct <warning>foo</warning>;
    """)

    fun testDirrectDeny() = checkByText("""
        #[deny(non_camel_case_types)]
        struct <warning>foo</warning>;
    """)

    fun testModuleOuterAllow() = checkByText("""
        #[allow(non_camel_case_types)]
        mod space {
            struct planet;
        }
    """)

    fun testModuleInnerAllow() = checkByText("""
        #![allow(non_camel_case_types)]
        struct planet;
    """)

    fun testGrandParentAllow() = checkByText("""
        #[allow(non_camel_case_types)]
        mod space {
            mod planet {
                struct inhabitant;
            }
        }
    """)

    fun testGrandParentAllowBadStyle() = checkByText("""
        #[allow(bad_style)]
        mod space {
            mod planet {
                struct inhabitant;
            }
        }
    """)

    fun testInnerTakesPrecedence() = checkByText("""
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

    fun testIgnoresOtherItems() = checkByText("""
        #[allow(non_snake_case)]
        struct <warning>foo</warning>;
    """)

    fun testMultipleMetaItems() = checkByText("""
        #[allow(non_snake_case, non_camel_case_types, non_upper_case_globals)]
        struct foo;
    """)

    fun testMixedAttributes() = checkByText("""
        #[allow(non_camel_case_types, non_upper_case_globals)]
        mod space {
            #[allow(non_snake_case)]
            mod planet {
                #![warn(non_snake_case, non_upper_case_globals)]
                struct inhabitant;
            }
        }
    """)

    fun testWorksWithNonLevelAttributes() = checkByText("""
        #[allow(non_camel_case_types)]
        #[derive(Debug)]
        struct inhabitant;
    """)
}

class RsLintLevelFieldTest : RsInspectionsTestBase(RsFieldNamingInspection(), true) {

    fun testParentAllow() = checkByText("""
        #[allow(non_snake_case)]
        struct Foo {
            Bar: u32
        }
    """)

    fun testParentWarn() = checkByText("""
        #[warn(non_snake_case)]
        struct Foo {
            <warning>Bar</warning>: u32
        }
    """)

    fun testParentDeny() = checkByText("""
        #[deny(non_snake_case)]
        struct Foo {
            <warning>Bar</warning>: u32
        }
    """)

}
