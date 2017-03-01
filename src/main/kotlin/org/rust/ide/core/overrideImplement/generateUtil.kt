package org.rust.ide.core.overrideImplement

import com.intellij.codeInsight.generation.ClassMember
import com.intellij.codeInsight.generation.MemberChooserObject
import com.intellij.codeInsight.generation.MemberChooserObjectBase
import com.intellij.ide.util.MemberChooser
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.project.Project
import com.intellij.ui.SimpleColoredComponent
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.impl.mixin.toImplementOverride
import org.rust.lang.core.psi.util.trait
import org.rust.utils.getFormattedParts
import java.util.*
import javax.swing.JTree

class RsTraitMemberChooserMember(val base: MemberChooserObjectBase, val member: RsNamedElement) : ClassMember {
    private val text: String;

    init {
        text = when (member) {
            is RsFunction -> {
                val (before, after) = member.getFormattedParts()
                "${member.name}$after"
            }
            is RsTypeAlias -> "${member.name}"
            is RsConstant -> "${member.name}: ${member.typeReference?.text}"
            else -> error("Unknown trait member: $member")
        }
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
    val trait = impl.traitRef?.trait ?: error("No trait ref")
    val traitName = trait.name ?: error("No trait name")

    val base = MemberChooserObjectBase(traitName, trait.getIcon(0))
    val (toImplement, toOverride) = impl.toImplementOverride(resolvedTrait = trait) ?: return null
    val mandatoryMembers = toImplement.map { RsTraitMemberChooserMember(base, it) }
    val allMembers = toOverride.map { RsTraitMemberChooserMember(base, it) }

    if (allMembers.isEmpty())
        return null

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
    if (selected.isEmpty())
        return
    val templateImpl = RsPsiFactory(impl.project).createTraitImplItem(
            selected.mapNotNull { it.member as? RsFunction },
            selected.mapNotNull { it.member as? RsTypeAlias },
            selected.mapNotNull { it.member as? RsConstant }
    )
    val lastMethodOrBrace = impl.functionList.lastOrNull() ?: impl.lbrace ?: return
    runWriteAction {
        impl.addRangeAfter(
                templateImpl.lbrace?.nextSibling,
                templateImpl.rbrace?.prevSibling,
                lastMethodOrBrace
        )
    }
}

fun generateTraitMembers(impl: RsImplItem) {
    val (all, selected) = createTraitMembersChooser(impl) ?: return
    val chooserSelected = showChooser(all, selected, impl.project);
    insertNewTraitMembers(chooserSelected, impl)
}