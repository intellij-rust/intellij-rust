/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.core.overrideImplement

import com.intellij.codeInsight.generation.ClassMember
import com.intellij.codeInsight.generation.MemberChooserObject
import com.intellij.codeInsight.generation.MemberChooserObjectBase
import com.intellij.codeInsight.hint.HintManager
import com.intellij.ide.util.MemberChooser
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.ui.SimpleColoredComponent
import org.rust.ide.utils.checkWriteAccessAllowed
import org.rust.ide.utils.presentationInfo
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.RsNamedElement
import org.rust.lang.core.psi.ext.resolveToTrait
import org.rust.lang.core.psi.ext.toImplementOverride
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

    override fun getParentNodeDelegate(): MemberChooserObject? {
        return base
    }

    override fun getText() = member.name ?: ""

    override fun equals(other: Any?): Boolean {
        return text == (other as? RsTraitMemberChooserMember)?.text
    }

    override fun hashCode() = text.hashCode()

    fun formattedText() = text
}

fun createTraitMembersChooser(impl: RsImplItem)
    : Pair<List<RsTraitMemberChooserMember>, List<RsTraitMemberChooserMember>>? {
    val trait = impl.traitRef?.resolveToTrait ?: return null
    val traitName = trait.name ?: return null

    val base = MemberChooserObjectBase(traitName, trait.getIcon(0))
    val (toImplement, toOverride) = impl.toImplementOverride(resolvedTrait = trait) ?: return null
    val mandatoryMembers = toImplement.map { RsTraitMemberChooserMember(base, it) }
    val allMembers = toOverride.map { RsTraitMemberChooserMember(base, it) }

    if (allMembers.isEmpty()) return null

    return allMembers to mandatoryMembers
}

private fun showChooser(all: Collection<RsTraitMemberChooserMember>,
                        selected: Collection<RsTraitMemberChooserMember>,
                        project: Project): Collection<RsTraitMemberChooserMember> {
    val chooser = MemberChooser(all.toTypedArray(), true, true, project)
    chooser.apply {
        title = "Implement Members"
        selectElements(selected.toTypedArray())
        setCopyJavadocVisible(false)
    }
    chooser.show()
    return chooser.selectedElements ?: listOf()
}

fun insertNewTraitMembers(selected: Collection<RsTraitMemberChooserMember>, impl: RsImplItem) {
    checkWriteAccessAllowed()
    if (selected.isEmpty())
        return
    val templateImpl = RsPsiFactory(impl.project).createTraitImplItem(
        selected.mapNotNull { it.member as? RsFunction },
        selected.mapNotNull { it.member as? RsTypeAlias },
        selected.mapNotNull { it.member as? RsConstant }
    )
    val lastMethodOrBrace = impl.functionList.lastOrNull() ?: impl.lbrace ?: return
    impl.addRangeAfter(
        templateImpl.lbrace?.nextSibling,
        templateImpl.rbrace?.prevSibling,
        lastMethodOrBrace
    )
}

fun generateTraitMembers(impl: RsImplItem, editor: Editor?) {
    check(!ApplicationManager.getApplication().isWriteAccessAllowed)
    val (all, selected) = createTraitMembersChooser(impl) ?: run {
        if (editor != null) {
            HintManager.getInstance().showErrorHint(editor, "No members to implement have been found")
        }
        return
    }
    val chooserSelected = showChooser(all, selected, impl.project)
    runWriteAction {
        insertNewTraitMembers(chooserSelected, impl)
    }
}
