/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.inspections

import org.rust.ProjectDescriptor
import org.rust.WithStdlibRustProjectDescriptor
import org.rust.ide.inspections.lints.RsSelfConventionInspection

/**
 * Tests for inspections suppression
 */
@ProjectDescriptor(WithStdlibRustProjectDescriptor::class)
class RsInspectionSuppressorTest : RsInspectionsTestBase(RsSelfConventionInspection::class) {

    fun `test without suppression`() = checkByText("""
        struct S;
        impl S {
            fn is_foo(<warning>s<caret>elf</warning>) { }
        }
    """)

    fun `test suppression`() = checkByText("""
        struct S;
        impl S {
            //noinspection RsSelfConvention
            fn is_foo(self) { }
            fn is_bar(<warning>s<caret>elf</warning>) { }
        }

        struct T;
        //noinspection RsSelfConvention
        impl T {
            fn is_foo(self) { }
            fn is_bar(self) { }
        }
    """)
}
