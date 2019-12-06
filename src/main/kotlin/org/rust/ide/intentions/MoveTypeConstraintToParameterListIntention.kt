/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.intentions

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.rust.lang.core.psi.RsBaseType
import org.rust.lang.core.psi.RsPsiFactory
import org.rust.lang.core.psi.RsTypeParameter
import org.rust.lang.core.psi.RsWhereClause
import org.rust.lang.core.psi.ext.*

class MoveTypeConstraintToParameterListIntention : RsElementBaseIntentionAction<RsWhereClause>() {

    override fun getText() = "Move type constraint to parameter list"
    override fun getFamilyName() = text

    override fun findApplicableContext(project: Project, editor: Editor, element: PsiElement): RsWhereClause? {
        val whereClause = element.ancestorStrict<RsWhereClause>() ?: return null
        val wherePredList = whereClause.wherePredList
        if (wherePredList.isEmpty()) return null

        val typeParameterList = whereClause.ancestorStrict<RsGenericDeclaration>()?.typeParameterList ?: return null
        val lifetimes = typeParameterList.lifetimeParameterList
        val types = typeParameterList.typeParameterList
        if (wherePredList.any {
                it.lifetime?.reference?.resolve() !in lifetimes &&
                    (it.typeReference?.typeElement as? RsBaseType)?.path?.reference?.resolve() !in types
            }) return null
        return whereClause
    }

    override fun invoke(project: Project, editor: Editor, ctx: RsWhereClause) {
        val declaration = ctx.ancestorStrict<RsGenericDeclaration>() ?: return

        val typeParameterList = declaration.typeParameterList ?: return
        val lifetimes = typeParameterList.lifetimeParameterList.mapNotNull { typeParameterText(it, it.bounds) }
        val types = typeParameterList.typeParameterList.mapNotNull { typeParameterText(it, it.bounds) }

        val newElement = RsPsiFactory(project).createTypeParameterList(lifetimes + types)
        val offset = typeParameterList.startOffset + newElement.textLength
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
                append(bs.joinToString(separator = "+", prefix = ":") { it.text })
            }
            val default = (param as? RsTypeParameter)?.typeReference?.text
            if (default != null) append("=$default")
        }
    }

}
