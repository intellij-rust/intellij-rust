/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.refactoring

import org.intellij.lang.annotations.Language
import org.rust.RsTestBase
import org.rust.ide.refactoring.introduceField.IntroduceFieldUi
import org.rust.ide.refactoring.introduceField.ParameterInfo
import org.rust.ide.refactoring.introduceField.withMockIntroduceFieldChooser
import org.rust.lang.core.psi.RsPsiFactory.BlockField
import org.rust.lang.core.psi.RsStructItem
import org.rust.lang.core.types.ty.TyInteger

class RsIntroduceFieldTest : RsTestBase() {
    fun `test empty block`() = doTest("""
        struct S {}
    """, listOf(BlockField(false, "a", TyInteger.I32)), """
        struct S {
            a: i32
        }
    """)

    fun `test block with existing fields`() = doTest("""
        struct S {
            a: u32,
            b: u32
        }
    """, listOf(BlockField(false, "c", TyInteger.I32)), """
        struct S {
            a: u32,
            b: u32,
            c: i32
        }
    """)

    fun `test respect block on one line`() = doTest("""
        struct S { a: u32, b: u32 };
    """, listOf(BlockField(false, "c", TyInteger.I32)), """
        struct S { a: u32, b: u32, c: i32 };
    """)

    fun `test empty tuple`() = doTest("""
        struct S();
    """, listOf(BlockField(false, "", TyInteger.I32)), """
        struct S(i32);
    """)

    fun `test tuple with existing fields`() = doTest("""
        struct S(u32, u32);
    """, listOf(BlockField(false, "", TyInteger.I32)), """
        struct S(u32, u32, i32);
    """)

    private fun doTest(
        @Language("Rust") before: String,
        fields: List<BlockField>,
        @Language("Rust") after: String
    ) {
        withMockIntroduceFieldChooser(object: IntroduceFieldUi {
            override fun introduceField(struct: RsStructItem): ParameterInfo? {
                return ParameterInfo(fields)
            }
        }) {
            checkEditorAction(before, after, "IntroduceField")
        }
    }
}
