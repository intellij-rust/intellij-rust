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
        override fun visitImplItem(implItem: RsImplItem) {
            val traitItems = targetItems(implItem.traitRef?.resolveToTrait).takeIf { it.isNotEmpty() } ?: return
            val implItems = targetItems(implItem).takeIf { it.isNotEmpty() } ?: return
            if (traitItems.size != implItems.size) return
            if (traitItems.zip(implItems).all { it.first.isSameItem(it.second) }) return
            holder.registerProblem(
                implItem,
                "Different impl member order from the trait",
                SortImplTraitMembersFix(traitItems, implItems)
            )
        }
    }

    private fun targetItems(item: RsTraitOrImpl?): List<RsItemElement> {
        val children = item?.members?.children
        return children?.mapNotNull {
            if (it is RsFunction || it is RsConstant || it is RsTypeAlias) it as? RsItemElement else null
        }?.takeIf { it.size == children.size } ?: emptyList()
    }

    private class SortImplTraitMembersFix(
        private val traitItems: List<RsItemElement>,
        private val implItems: List<RsItemElement>
    ) : LocalQuickFix {
        override fun getName() = "Apply same member order"

        override fun getFamilyName() = name

        override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
            val implItemMap = implItems.associate { it.name + it.elementType to it }
            val sortedImplItems = traitItems.mapNotNull { implItemMap[it.name + it.elementType]?.copy() }
            traitItems.zip(implItems).forEachIndexed { index, (traitItem, implItem) ->
                if (!traitItem.isSameItem(implItem)) implItem.replace(sortedImplItems[index])
            }
        }
    }
}

private fun RsItemElement.isSameItem(other: RsItemElement): Boolean = name == other.name && elementType == other.elementType
