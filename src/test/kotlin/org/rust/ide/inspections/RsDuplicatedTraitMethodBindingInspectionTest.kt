/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.inspections

import org.intellij.lang.annotations.Language

class RsDuplicatedTraitMethodBindingInspectionTest : RsInspectionsTestBase(RsDuplicatedTraitMethodBindingInspection::class) {
    fun `test duplicated`() = doTest("""
        trait Foo {
            fn bar(
                <weak_warning descr="Duplicated parameter name `x`. Consider renaming it">x</weak_warning>: i32,
                <weak_warning descr="Duplicated parameter name `x`. Consider renaming it">x</weak_warning>: i32
            );
        }
    """)

    fun `test not duplicated`() = doTest("""
        trait Foo {
            fn bar(x: i32, y: i32);
        }
    """)

    fun `test trait method with body`() = doTest("""
        trait Foo {
            fn bar(x: i32, x: i32) {}
        }
    """)

    fun `test impl method`() = doTest("""
        struct S;
        impl S {
            fn bar(x: i32, x: i32);
        }
    """)

    private fun doTest(@Language("Rust") code: String) = checkByText(code, checkWeakWarn = true)
}
