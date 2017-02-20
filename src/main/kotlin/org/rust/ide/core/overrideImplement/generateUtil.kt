package org.rust.ide.core.overrideImplement

import com.intellij.codeInsight.generation.ClassMember
import com.intellij.codeInsight.generation.MemberChooserObject
import com.intellij.codeInsight.generation.MemberChooserObjectBase
import com.intellij.ide.util.MemberChooser
import com.intellij.openapi.application.runWriteAction
import com.intellij.ui.SimpleColoredComponent
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.impl.mixin.*
import org.rust.lang.core.psi.util.trait
import org.rust.utils.getFormattedParts
import javax.swing.JTree

private class RsTraitMemberChooserMember(val base: MemberChooserObjectBase, val member: RsNamedElement) : ClassMember {
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
}

fun generateTraitMembers(impl: RsImplItem) {
    val project = impl.project
    val trait = impl.traitRef?.trait ?: error("No trait ref")
    val traitName = trait.name ?: error("No trait name")

    val base = MemberChooserObjectBase(traitName, trait.getIcon(0))
    val (toImplement, toOverride) = impl.toImplementOverride() ?: return
    val mandatoryMembers = toImplement.map { RsTraitMemberChooserMember(base, it) }
    val allMembers = toOverride.map { RsTraitMemberChooserMember(base, it) }
    
    if (allMembers.isEmpty())
        return

    val chooser = MemberChooser(allMembers.toTypedArray(), true, true, project)
    chooser.title = "Implement Members"
    chooser.selectElements(mandatoryMembers.toTypedArray())
    chooser.setCopyJavadocVisible(false)
    chooser.show()

    val selected = chooser.selectedElements ?: listOf()
    if (selected.isEmpty())
        return
    val templateImpl = RsPsiFactory(project).createTraitImplItem(
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
