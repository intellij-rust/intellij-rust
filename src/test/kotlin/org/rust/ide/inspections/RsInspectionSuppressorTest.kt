/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.inspections

/**
 * Tests for inspections suppression
 */
class RsInspectionSuppressorTest : RsInspectionsTestBase(RsSelfConventionInspection::class) {

    fun testWithoutSuppression() = checkByText("""
        struct S;
        impl S {
            fn is_foo(<warning>s<caret>elf</warning>) { }
        }
    """)

    fun testSuppression() = checkByText("""
        struct S;
        impl S {
            //noinspection RsSelfConvention
            fn is_foo(self) { }
            fn is_bar(<warning>s<caret>elf</warning>) { }
            
            // comment1
            //noinspection RsSelfConvention
            // comment2
            fn is_baz(self) { }
            
            // comment1
            //noinspection RsSelfConvention
            /// doc comment1
            fn is_quux(self) { }
        }

        struct T;
        //noinspection RsSelfConvention
        impl T {
            fn is_foo(self) { }
            fn is_bar(self) { }
        }
    """)
}
