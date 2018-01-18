/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.intentions

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.rust.lang.core.psi.RsImplItem
import org.rust.lang.core.psi.ext.ancestorStrict
import org.rust.lang.core.psi.ext.resolveToTrait

class SortImplTraitMethodsIntention : RsElementBaseIntentionAction<RsImplItem>() {

    override fun getText() = "Sort methods in the same order as in the trait"
    override fun getFamilyName(): String = text

    override fun findApplicableContext(project: Project, editor: Editor, element: PsiElement): RsImplItem? {
        val implItem = element.ancestorStrict<RsImplItem>() ?: return null
        if (!isApplicableTo(implItem)) return null
        return implItem
    }

    override fun invoke(project: Project, editor: Editor, ctx: RsImplItem) {
        val implMethods = ctx.members?.functionList ?: return
        val traitMethods = ctx.traitRef?.resolveToTrait?.members?.functionList ?: return

        val implMethodMap = implMethods.map { it.name to it }.toMap()
        val sortedImplMethods = traitMethods.mapNotNull { implMethodMap[it.name]?.copy() }

        traitMethods.zip(implMethods).forEachIndexed { index, (traitMethod, implMethod) ->
            if (traitMethod.name != implMethod.name) implMethod.replace(sortedImplMethods[index])
        }
    }

    companion object {
        fun isApplicableTo(implItem: RsImplItem): Boolean {
            val implMethods = implItem.members?.functionList ?: return false
            val traitMethods = implItem.traitRef?.resolveToTrait?.members?.functionList ?: return false

            if (implMethods.size != traitMethods.size) return false
            if (traitMethods.zip(implMethods).all { it.first.name == it.second.name }) return false

            return true
        }
    }
}
