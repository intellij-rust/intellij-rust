/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.refactoring.generateConstructor

import com.intellij.codeInsight.generation.ClassMember
import com.intellij.codeInsight.generation.MemberChooserObject
import com.intellij.codeInsight.generation.MemberChooserObjectBase
import com.intellij.ide.util.MemberChooser
import com.intellij.openapi.project.Project
import com.intellij.openapiext.isUnitTestMode
import org.jetbrains.annotations.TestOnly
import org.rust.ide.icons.RsIcons
import org.rust.lang.core.psi.RsStructItem
import org.rust.lang.core.types.Substitution

private var MOCK: StructMemberChooserUi? = null

fun showConstructorArgumentsChooser(
    project: Project,
    structItem: RsStructItem,
    substitution: Substitution
): List<ConstructorArgument>? {
    val chooser = if (isUnitTestMode) {
        MOCK ?: error("You should set mock ui via `withMockStructMemberChooserUi`")
    } else {
        DialogStructMemberChooserUi
    }
    val base = MemberChooserObjectBase(structItem.name, structItem.getIcon(0))
    val arguments = ConstructorArgument.fromStruct(structItem, substitution).map { RsStructMemberChooserObject(base, it) }
    return chooser.selectMembers(project, arguments)?.map { it.member }
}

@TestOnly
fun withMockStructMemberChooserUi(mockUi: StructMemberChooserUi, action: () -> Unit) {
    MOCK = mockUi
    try {
        action()
    } finally {
        MOCK = null
    }
}

class RsStructMemberChooserObject(
    val base: MemberChooserObjectBase,
    val member: ConstructorArgument
) : MemberChooserObjectBase(member.dialogRepresentation, RsIcons.FIELD),
    ClassMember {

    override fun getParentNodeDelegate(): MemberChooserObject? = base
    override fun equals(other: Any?): Boolean = member == (other as? RsStructMemberChooserObject)?.member
    override fun hashCode() = text.hashCode()
}

interface StructMemberChooserUi {
    fun selectMembers(project: Project, all: List<RsStructMemberChooserObject>): List<RsStructMemberChooserObject>?
}

private object DialogStructMemberChooserUi : StructMemberChooserUi {

    override fun selectMembers(project: Project, all: List<RsStructMemberChooserObject>): List<RsStructMemberChooserObject>? {
        val chooser = MemberChooser(all.toTypedArray(), true, true, project).apply {
            title = "Generate Constructor"
            selectElements(all.toTypedArray())
            setCopyJavadocVisible(false)
        }
        return if (all.isNotEmpty()) {
            chooser.show()
            chooser.selectedElements
        } else {
            all
        }
    }
}
