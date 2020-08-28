/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.refactoring.generate

import com.intellij.openapi.actionSystem.ex.ActionManagerEx
import com.intellij.openapi.project.Project
import org.intellij.lang.annotations.Language
import org.rust.RsTestBase

abstract class RsGenerateBaseTest : RsTestBase() {
    abstract val generateId: String

    protected data class MemberSelection(val member: String, val isSelected: Boolean = true)

    protected fun doTest(
        @Language("Rust") code: String,
        chooser: List<MemberSelection>,
        @Language("Rust") expected: String
    ) {
        withMockStructMemberChooserUi(object : StructMemberChooserUi {
            override fun selectMembers(
                project: Project,
                all: List<RsStructMemberChooserObject>
            ): List<RsStructMemberChooserObject>? {
                assertEquals(chooser.map { it.member }, all.map { it.text })
                val selected = chooser.filter { it.isSelected }.map { it.member }
                return all.filter { it.text in selected }
            }
        }) {
            checkEditorAction(code, expected, generateId)
        }
    }

    protected fun doUnavailableTest(@Language("Rust") code: String) {
        InlineFile(code)

        withMockStructMemberChooserUi(object : StructMemberChooserUi {
            override fun selectMembers(project: Project, all: List<RsStructMemberChooserObject>): List<RsStructMemberChooserObject>? {
                error("unreachable")
            }
        }) {
            val presentation = myFixture.testAction(ActionManagerEx.getInstanceEx().getAction(generateId))
            check(!presentation.isEnabled)
        }
    }
}
