/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.intentions

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.*

class MoveTypeConstraintToParameterListIntention : RsElementBaseIntentionAction<RsWhereClause>() {

    override fun getText() = "Move type constraint to parameter list"
    override fun getFamilyName() = text

    override fun findApplicableContext(project: Project, editor: Editor, element: PsiElement): RsWhereClause? {
        val whereClause = element.ancestorStrict<RsWhereClause>() ?: return null
        val wherePredList = whereClause.wherePredList
        if (wherePredList.isEmpty()) return null

        val typeParameterList = whereClause.ancestorStrict<RsGenericDeclaration>()?.typeParameterList ?: return null
        val lifetimes = typeParameterList.lifetimeParameterList.mapNotNull { it.quoteIdentifier.text }
        val types = typeParameterList.typeParameterList.mapNotNull { it.name }
        if (wherePredList.any { it.lifetime?.quoteIdentifier?.text !in lifetimes && it.typeReference?.text !in types })
            return null

        return whereClause
    }

    override fun invoke(project: Project, editor: Editor, ctx: RsWhereClause) {
        val declaration = ctx.ancestorStrict<RsGenericDeclaration>() ?: return

        val typeParameterList = declaration.typeParameterList ?: return
        val lifetimes = typeParameterList.lifetimeParameterList.mapNotNull { typeParameterText(it, it.bounds) }
        val types = typeParameterList.typeParameterList.mapNotNull { typeParameterText(it, it.bounds) }

        val newElement = RsPsiFactory(project).createTypeParameterList(lifetimes + types)
        val offset = typeParameterList.textRange.startOffset + newElement.textLength
        typeParameterList.replace(newElement)
        ctx.delete()
        editor.caretModel.moveToOffset(offset)
    }

    private fun typeParameterText(param: RsNamedElement, bounds: List<RsElement>): String? {
        val name = param.name ?: return null
        val bs = bounds.distinctBy { it.text }
        return buildString {
            append(name)
            if (bs.isNotEmpty()) {
                append(":")
                append(bs.joinToString("+") { it.text })
            }
        }
    }

}
