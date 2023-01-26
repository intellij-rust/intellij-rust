/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.inspections

import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.tree.IElementType
import org.rust.lang.core.psi.RsImplItem
import org.rust.lang.core.psi.RsVisitor
import org.rust.lang.core.psi.ext.*
import org.rust.openapiext.Testmark

class RsSortImplTraitMembersInspection : RsLocalInspectionTool() {
    override fun buildVisitor(holder: RsProblemsHolder, isOnTheFly: Boolean): RsVisitor = object : RsWithMacrosInspectionVisitor() {
        @Suppress("PARAMETER_NAME_CHANGED_ON_OVERRIDE")
        override fun visitImplItem2(impl: RsImplItem) {
            val trait = impl.traitRef?.resolveToTrait() ?: return
            val typeRef = impl.typeReference ?: return
            if (sortedImplItems(impl.explicitMembers, trait.explicitMembers) == null) return
            val textRange = TextRange(
                (impl.vis ?: impl.default ?: impl.unsafe ?: impl.impl).startOffsetInParent,
                typeRef.startOffsetInParent + typeRef.textLength
            )
            holder.registerProblem(
                impl, textRange,
                "Different impl member order from the trait",
                SortImplTraitMembersFix()
            )
        }
    }

    companion object {
        private fun sortedImplItems(implItems: List<RsAbstractable>, traitItems: List<RsAbstractable>): List<RsAbstractable>? {
            val traitItemMap = traitItems.withIndex().associate { it.value.key() to it.index }
            if (implItems.any { it.key() !in traitItemMap }) {
                Testmarks.ImplMemberNotInTrait.hit()
                return null
            }
            val sortedImplItems = implItems.sortedBy { traitItemMap[it.key()] }
            if (sortedImplItems == implItems) return null
            return sortedImplItems
        }
    }

    private class SortImplTraitMembersFix : LocalQuickFix {
        override fun getFamilyName() = "Apply same member order"

        override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
            val impl = descriptor.psiElement as? RsImplItem ?: return
            val trait = impl.traitRef?.resolveToTrait() ?: return
            val implItems = impl.explicitMembers
            val traitItems = trait.explicitMembers

            // As we're applying the fix, this will be non-null.
            val sortedImplItems = sortedImplItems(implItems, traitItems) ?: return
            implItems.zip(sortedImplItems).forEachIndexed { index, (implItem, expectedImplItem) ->
                if (implItem.key() != expectedImplItem.key())
                    implItem.replace(sortedImplItems[index].copy())
            }
        }
    }

    object Testmarks {
        object ImplMemberNotInTrait : Testmark()
    }
}

private fun RsAbstractable.key(): Pair<String?, IElementType> = name to elementType
