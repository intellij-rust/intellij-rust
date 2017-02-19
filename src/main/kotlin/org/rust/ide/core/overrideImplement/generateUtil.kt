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

sealed class TraitMember {
    class Function(val function: RsFunction) : TraitMember()
    class Type(val type: RsTypeAlias) : TraitMember()
    class Constant(val constant: RsConstant) : TraitMember()
}

private class RsFunctionChooserMember(val base: MemberChooserObjectBase, val member: TraitMember) : ClassMember {
    private val text: String;

    init {
        when (member) {
            is TraitMember.Function -> {
                val (before, after) = member.function.getFormattedParts()
                text = "${member.function.name}$after"
            }
            is TraitMember.Type -> {
                text = "${member.type.name}"
            }
            is TraitMember.Constant -> {
                text = "${member.constant.name}: ${member.constant.typeReference?.text}"
            }
        }
    }

    override fun renderTreeNode(component: SimpleColoredComponent?, tree: JTree?) {
        component?.icon = when (member) {
            is TraitMember.Function -> member.function
            is TraitMember.Type -> member.type
            is TraitMember.Constant -> member.constant
        }.getIcon(0)
        component?.append(text)
    }

    override fun getParentNodeDelegate(): MemberChooserObject? {
        return base
    }

    override fun getText() = when (member) {
        is TraitMember.Function -> member.function.name
        is TraitMember.Type -> member.type.name
        is TraitMember.Constant -> member.constant.name
    } ?: ""

    override fun equals(other: Any?): Boolean {
        return text == (other as? RsFunctionChooserMember)?.text
    }

    override fun hashCode() = text.hashCode()
}

fun generateTraitMembers(impl: RsImplItem) {
    val project = impl.project
    val trait = impl.traitRef?.trait ?: error("No trait ref")
    val traitName = trait.name ?: error("No trait name")

    val base = MemberChooserObjectBase(traitName, trait.getIcon(0))
    val mandatoryMembers = impl.toImplementFunctions().map { RsFunctionChooserMember(base, TraitMember.Function(it)) } +
        impl.toImplementTypes().map { RsFunctionChooserMember(base, TraitMember.Type(it)) } +
        impl.toImplementConstants().map { RsFunctionChooserMember(base, TraitMember.Constant(it)) }
    val allMembers = impl.canOverrideFunctions().map { RsFunctionChooserMember(base, TraitMember.Function(it)) } +
        impl.canOverrideTypeAliases().map { RsFunctionChooserMember(base, TraitMember.Type(it)) } +
        impl.canOverrideConstants().map { RsFunctionChooserMember(base, TraitMember.Constant(it)) }

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
        selected.mapNotNull { (it.member as? TraitMember.Function)?.function },
        selected.mapNotNull { (it.member as? TraitMember.Type)?.type },
        selected.mapNotNull { (it.member as? TraitMember.Constant)?.constant }
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
