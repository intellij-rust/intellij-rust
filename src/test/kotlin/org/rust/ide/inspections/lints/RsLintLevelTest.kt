/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.inspections.lints

import org.rust.ide.inspections.RsInspectionsTestBase

/**
 * Tests for lint level detection.
 */
class RsLintLevelStructTest : RsInspectionsTestBase(RsStructNamingInspection::class) {

    fun `test direct allow`() = checkByText("""
        #[allow(non_camel_case_types)]
        struct foo;
    """)

    fun `test direct allow bad style`() = checkByText("""
        #[allow(bad_style)]
        struct foo;
    """)

    fun `test direct warn`() = checkByText("""
        #[warn(non_camel_case_types)]
        struct <warning>foo</warning>;
    """)

    fun `test direct deny`() = checkByText("""
        #[deny(non_camel_case_types)]
        struct <error>foo</error>;
    """)

    fun `test module outer allow`() = checkByText("""
        #[allow(non_camel_case_types)]
        mod space {
            struct planet;
        }
    """)

    fun `test module inner allow`() = checkByText("""
        #![allow(non_camel_case_types)]
        struct planet;
    """)

    fun `test grand parent allow`() = checkByText("""
        #[allow(non_camel_case_types)]
        mod space {
            mod planet {
                struct inhabitant;
            }
        }
    """)

    fun `test grand parent allow bad style`() = checkByText("""
        #[allow(bad_style)]
        mod space {
            mod planet {
                struct inhabitant;
            }
        }
    """)

    fun `test inner takes precedence`() = checkByText("""
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

    fun `test ignores other items`() = checkByText("""
        #[allow(non_snake_case)]
        struct <warning>foo</warning>;
    """)

    fun `test multiple meta items`() = checkByText("""
        #[allow(non_snake_case, non_camel_case_types, non_upper_case_globals)]
        struct foo;
    """)

    fun `test mixed attributes`() = checkByText("""
        #[allow(non_camel_case_types, non_upper_case_globals)]
        mod space {
            #[allow(non_snake_case)]
            mod planet {
                #![warn(non_snake_case, non_upper_case_globals)]
                struct inhabitant;
            }
        }
    """)

    fun `test works with non level attributes`() = checkByText("""
        #[allow(non_camel_case_types)]
        #[derive(Debug)]
        struct inhabitant;
    """)

    fun `test direct forbid`() = checkByText("""
        #[forbid(non_camel_case_types)]
        struct <error>inhabitant</error>;
    """)

    fun `test parent indirectly forbid`() = checkByText("""
        #[forbid(non_camel_case_types)]
        pub mod m1 {
            struct <error>inhabitant</error>;
        }
    """)
}

class RsLintLevelFieldTest : RsInspectionsTestBase(RsFieldNamingInspection::class) {

    fun `test parent allow`() = checkByText("""
        #[allow(non_snake_case)]
        struct Foo {
            Bar: u32
        }
    """)

    fun `test parent warn`() = checkByText("""
        #[warn(non_snake_case)]
        struct Foo {
            <warning>Bar</warning>: u32
        }
    """)

    fun `test parent deny`() = checkByText("""
        #[deny(non_snake_case)]
        struct Foo {
            <error>Bar</error>: u32
        }
    """)

    fun `test parent forbid`() = checkByText("""
        #[forbid(non_snake_case)]
        struct Foo {
            <error>Bar</error>: u32
        }
    """)

}
