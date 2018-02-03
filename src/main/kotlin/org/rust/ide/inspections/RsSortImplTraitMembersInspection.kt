/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.inspections

import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.project.Project
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.RsItemElement
import org.rust.lang.core.psi.ext.RsTraitOrImpl
import org.rust.lang.core.psi.ext.elementType
import org.rust.lang.core.psi.ext.resolveToTrait

class RsSortImplTraitMembersInspection : RsLocalInspectionTool() {
    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean) = object : RsVisitor() {
        override fun visitImplItem(impl: RsImplItem) {
            val trait = impl.traitRef?.resolveToTrait ?: return
            if (sortedImplItems(impl.items(), trait.items()) == null) return
            holder.registerProblem(
                impl,
                "Different impl member order from the trait",
                SortImplTraitMembersFix()
            )
        }
    }

    companion object {
        private fun sortedImplItems(implItems: List<RsItemElement>, traitItems: List<RsItemElement>): List<RsItemElement>? {
            val implItemMap = implItems.associate { it.key() to it }
            val sortedImplItems = traitItems.mapNotNull { implItemMap[it.key()] }
            if (sortedImplItems.size != implItems.size || sortedImplItems == implItems) return null
            return sortedImplItems
        }
    }

    private class SortImplTraitMembersFix : LocalQuickFix {
        override fun getFamilyName() = "Apply same member order"

        override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
            val impl = descriptor.psiElement as? RsImplItem ?: return
            val trait = impl.traitRef?.resolveToTrait ?: return
            val implItems = impl.items()
            val traitItems = trait.items()
            val sortedImplItems = sortedImplItems(implItems, traitItems)?.map { it.copy() } ?: return
            traitItems.zip(implItems).forEachIndexed { index, (traitItem, implItem) ->
                if (traitItem.key() != implItem.key()) implItem.replace(sortedImplItems[index])
            }
        }
    }
}

private fun RsTraitOrImpl.items(): List<RsItemElement> = members?.children?.mapNotNull {
    if (it is RsFunction || it is RsConstant || it is RsTypeAlias) it as? RsItemElement else null
} ?: emptyList()

private fun RsItemElement.key() = name to elementType
