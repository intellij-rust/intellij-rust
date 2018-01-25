/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.intentions

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.RsItemElement
import org.rust.lang.core.psi.ext.RsTraitOrImpl
import org.rust.lang.core.psi.ext.ancestorStrict
import org.rust.lang.core.psi.ext.resolveToTrait

class SortImplTraitMembersIntention : RsElementBaseIntentionAction<SortImplTraitMembersIntention.Context>() {

    override fun getText() = "Apply same member order"
    override fun getFamilyName(): String = text

    override fun findApplicableContext(project: Project, editor: Editor, element: PsiElement): Context? {
        val implItem = element.ancestorStrict<RsImplItem>() ?: return null
        return isApplicableTo(implItem)
    }

    override fun invoke(project: Project, editor: Editor, ctx: Context) {
        val (traitItems, implItems) = ctx

        val implItemMap = implItems.associate { it.name to it }
        val sortedImplItems = traitItems.mapNotNull { implItemMap[it.name]?.copy() }

        traitItems.zip(implItems).forEachIndexed { index, (traitItem, implItem) ->
            if (traitItem.name != implItem.name) implItem.replace(sortedImplItems[index])
        }
    }

    companion object {
        fun isApplicableTo(implItem: RsImplItem): Context? {
            val traitItems = targetItems(implItem.traitRef?.resolveToTrait).takeIf { it.isNotEmpty() } ?: return null
            val implItems =  targetItems(implItem).takeIf { it.isNotEmpty() } ?: return null

            if (traitItems.size != implItems.size) return null
            if (traitItems.zip(implItems).all { it.first.name == it.second.name }) return null

            return Context(traitItems = traitItems, implItems = implItems)
        }

        private fun targetItems(item: RsTraitOrImpl?): List<RsItemElement> {
            val children = item?.members?.children
            return children?.mapNotNull {
                if (it is RsFunction || it is RsConstant || it is RsTypeAlias) it as? RsItemElement else null
            }?.takeIf { it.size == children.size } ?: emptyList()
        }
    }

    data class Context(
        val traitItems: List<RsItemElement>,
        val implItems: List<RsItemElement>
    )

}
