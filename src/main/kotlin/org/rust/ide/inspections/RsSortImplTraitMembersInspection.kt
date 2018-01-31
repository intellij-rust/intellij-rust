/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.inspections

import com.intellij.codeInspection.LocalQuickFixOnPsiElement
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
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
                SortImplTraitMembersFix(impl, trait)
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

    private class SortImplTraitMembersFix(impl: RsImplItem, trait: RsTraitItem) : LocalQuickFixOnPsiElement(impl, trait) {
        override fun getText() = "Apply same member order"

        override fun getFamilyName() = name

        override fun invoke(project: Project, file: PsiFile, startElement: PsiElement, endElement: PsiElement) {
            val implItems = (startElement as? RsImplItem)?.items() ?: return
            val traitItems = (endElement as? RsTraitItem)?.items() ?: return
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
