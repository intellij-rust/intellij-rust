package org.rust.ide.core.overrideImplement

import com.intellij.codeInsight.generation.ClassMember
import com.intellij.codeInsight.generation.MemberChooserObject
import com.intellij.codeInsight.generation.MemberChooserObjectBase
import com.intellij.ide.util.MemberChooser
import com.intellij.openapi.application.runWriteAction
import com.intellij.psi.PsiElement
import com.intellij.ui.SimpleColoredComponent
import com.intellij.ui.SimpleTextAttributes
import io.netty.util.internal.chmv8.ConcurrentHashMapV8
import org.rust.ide.icons.RsIcons
import org.rust.ide.utils.toImplementFunctions
import org.rust.ide.utils.toImplementTypes
import org.rust.lang.core.psi.RsFunction
import org.rust.lang.core.psi.RsImplItem
import org.rust.lang.core.psi.RsPsiFactory
import org.rust.lang.core.psi.RsTypeAlias
import org.rust.lang.core.psi.impl.mixin.default
import org.rust.lang.core.psi.impl.mixin.isAbstract
import org.rust.lang.core.psi.util.childOfType
import org.rust.lang.core.psi.util.trait
import org.rust.utils.getFormattedParts
import javax.swing.JTree

sealed class FunctionOrType {
    class Function(val function: RsFunction) : FunctionOrType()
    class Type(val type: RsTypeAlias) : FunctionOrType()
}

private class RsFunctionChooserMember(val base: MemberChooserObjectBase, val member: FunctionOrType) : ClassMember {
    private val text: String;

    init {
        when (member) {
            is FunctionOrType.Function -> {
                val (before, after) = member.function.getFormattedParts()
                text = "${member.function.name}$after"
            }
            is FunctionOrType.Type -> {
                text = "${member.type.name}"
            }
        }
    }

    override fun renderTreeNode(component: SimpleColoredComponent?, tree: JTree?) {
        component?.icon = when (member) {
            is FunctionOrType.Function -> member.function
            is FunctionOrType.Type -> member.type
        }.getIcon(0)
        component?.append(text)
    }

    override fun getParentNodeDelegate(): MemberChooserObject? {
        return base
    }

    override fun getText() = when (member) {
        is FunctionOrType.Function -> member.function.name ?: ""
        is FunctionOrType.Type -> member.type.name ?: ""
    }
}

fun generateTraitMembers(impl: RsImplItem) {
    val project = impl.project
    val trait = impl.traitRef?.trait ?: error("No trait ref")
    val traitName = trait.name ?: error("No trait name")

    val toImplement = impl.toImplementFunctions()
    val toImplementTypes = impl.toImplementTypes()

    if (toImplement.isEmpty() && toImplementTypes.isEmpty())
        return
    val base = MemberChooserObjectBase(traitName, RsIcons.TRAIT)
    val members = toImplement.map { RsFunctionChooserMember(base, FunctionOrType.Function(it)) } +
            toImplementTypes.map { RsFunctionChooserMember(base, FunctionOrType.Type(it)) }

    val chooser = MemberChooser(members.toTypedArray(), true, true, project)
    chooser.title = "Implement Members"
    chooser.setCopyJavadocVisible(false)
    chooser.show()

    val selected = chooser.selectedElements ?: listOf()
    if (selected.isEmpty())
        return
    val templateImpl = RsPsiFactory(project).createTraitImplItem(
            selected.mapNotNull { (it.member as? FunctionOrType.Function)?.function },
            selected.mapNotNull { (it.member as? FunctionOrType.Type)?.type }
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
