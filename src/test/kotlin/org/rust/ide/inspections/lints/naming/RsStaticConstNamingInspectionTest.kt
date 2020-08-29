/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.inspections.lints.naming

import org.rust.ide.inspections.RsInspectionsTestBase
import org.rust.ide.inspections.lints.RsStaticConstNamingInspection

class RsStaticConstNamingInspectionTest : RsInspectionsTestBase(RsStaticConstNamingInspection::class) {
    fun `test statics`() = checkByText("""
        static STATIC_OK: u32 = 12;
        static <warning descr="Static constant `static_foo` should have an upper case name such as `STATIC_FOO`">static_foo</warning>: u32 = 12;
    """)

    fun `test statics suppression`() = checkByText("""
        #[allow(non_upper_case_globals)]
        static static_foo: u32 = 12;
    """)

    fun `test statics fix`() = checkFixByText("Rename to `STATIC_FOO`", """
        static <warning descr="Static constant `staticFoo` should have an upper case name such as `STATIC_FOO`">sta<caret>ticFoo</warning>: u32 = 43;
        fn static_use() {
            let a = staticFoo;
        }
    """, """
        static STATIC_FOO: u32 = 43;
        fn static_use() {
            let a = STATIC_FOO;
        }
    """)

    fun `test extern static`() = checkByText("""
        extern {
            static Bar: i32;
        }
    """)
}
