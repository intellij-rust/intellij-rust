/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.inspections.lints.naming

import org.rust.SkipTestWrapping
import org.rust.ide.inspections.RsInspectionsTestBase
import org.rust.ide.inspections.lints.RsStructNamingInspection

class RsStructNamingInspectionTest : RsInspectionsTestBase(RsStructNamingInspection::class) {
    fun `test structs`() = checkByText("""
        struct StructOk {}
        struct <warning descr="Type `struct_foo` should have a camel case name such as `StructFoo`">struct_foo</warning> {}
    """)

    fun `test structs suppression`() = checkByText("""
        #[allow(non_camel_case_types)]
        struct struct_foo {}
    """)

    @SkipTestWrapping // TODO support `RenameFix` in macros
    fun `test structs fix`() = checkFixByText("Rename to `StructFoo`", """
        struct <warning descr="Type `Struct_foo` should have a camel case name such as `StructFoo`">Stru<caret>ct_foo</warning> {}
        fn struct_use() {
            let a = Struct_foo {};
        }
    """, """
        struct StructFoo {}
        fn struct_use() {
            let a = StructFoo {};
        }
    """, preview = null)

    @SkipTestWrapping // TODO support `RenameFix` in macros
    fun `test struct with raw identifier`() = checkFixByText("Rename to `FooBar`", """
        struct <warning descr="Type `foo_bar` should have a camel case name such as `FooBar`">r#foo_bar/*caret*/</warning>;
        fn main() {
            let a = foo_bar;
        }
    """, """
        struct FooBar;
        fn main() {
            let a = FooBar;
        }
    """, preview = null)

    fun `test struct not support case`() = checkByText("""
       struct 结构体;
       struct 結構;
       struct 구조체;
       struct 構造体;
    """)
}
