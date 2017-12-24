/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.refactoring.implementMembers

import com.intellij.codeInsight.generation.ClassMember
import com.intellij.codeInsight.generation.MemberChooserObject
import com.intellij.codeInsight.generation.MemberChooserObjectBase
import com.intellij.ide.util.MemberChooser
import com.intellij.openapi.project.Project
import com.intellij.ui.SimpleColoredComponent
import org.jetbrains.annotations.TestOnly
import org.rust.ide.presentation.presentationInfo
import org.rust.lang.core.psi.RsConstant
import org.rust.lang.core.psi.RsFunction
import org.rust.lang.core.psi.RsTypeAlias
import org.rust.lang.core.psi.ext.RsNamedElement
import org.rust.lang.core.psi.ext.TraitImplementationInfo
import org.rust.openapiext.isUnitTestMode
import javax.swing.JTree


class RsTraitMemberChooserMember(val base: MemberChooserObjectBase, val member: RsNamedElement) : ClassMember {
    private val text: String = when (member) {
        is RsFunction ->
            member.presentationInfo?.projectStructureItemText ?: ""
        is RsTypeAlias -> "${member.name}"
        is RsConstant -> "${member.name}: ${member.typeReference?.text}"
        else -> error("Unknown trait member: $member")
    }

    override fun renderTreeNode(component: SimpleColoredComponent?, tree: JTree?) {
        component?.icon = member.getIcon(0)
        component?.append(text)
    }

    override fun getParentNodeDelegate(): MemberChooserObject? = base

    override fun getText() = member.name ?: ""

    override fun equals(other: Any?): Boolean {
        return text == (other as? RsTraitMemberChooserMember)?.text
    }

    override fun hashCode() = text.hashCode()

    fun formattedText() = text
}

fun showTraitMemberChooser(
    implInfo: TraitImplementationInfo,
    project: Project
): Collection<RsNamedElement> {

    val base = MemberChooserObjectBase(implInfo.traitName, implInfo.trait.getIcon(0))
    val all = implInfo.declared.map { RsTraitMemberChooserMember(base, it) }
    val nonImplemented = all.filter { it.member !in implInfo.alreadyImplemented }
    val selectedByDefault = nonImplemented.filter { it.member in implInfo.missingImplementations }
    val chooser = if (isUnitTestMode) MOCK!! else memberChooserDialog
    return chooser(project, nonImplemented, selectedByDefault).map { it.member }
}

typealias TraitMemberChooser = (
    project: Project,
    all: List<RsTraitMemberChooserMember>,
    selectedByDefault: List<RsTraitMemberChooserMember>
) -> List<RsTraitMemberChooserMember>

private val memberChooserDialog: TraitMemberChooser = { project, all, selectedByDefault ->
    val chooser = MemberChooser(all.toTypedArray(), true, true, project).apply {
        title = "Implement Members"
        selectElements(selectedByDefault.toTypedArray())
        setCopyJavadocVisible(false)
    }
    chooser.show()
    chooser.selectedElements.orEmpty()
}

private var MOCK: TraitMemberChooser? = null
@TestOnly
fun withMockTraitMemberChooser(
    mock: TraitMemberChooser,
    f: () -> Unit
) {
    MOCK = { project, all, selectedByDefault ->
        val result = mock(project, all, selectedByDefault)
        MOCK = null
        result
    }
    try {
        f()
        check(MOCK == null) { "Selector was not called" }
    } finally {
        MOCK = null
    }
}
