/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.intentions

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.rust.RsBundle
import org.rust.ide.intentions.util.macros.InvokeInside
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.*
import org.rust.openapiext.moveCaretToOffset

class MoveTypeConstraintToParameterListIntention : RsElementBaseIntentionAction<RsWhereClause>() {
    override fun getText() = RsBundle.message("intention.name.move.type.constraint.to.parameter.list")
    override fun getFamilyName() = text

    override val attributeMacroHandlingStrategy: InvokeInside get() = InvokeInside.MACRO_CALL

    override fun findApplicableContext(project: Project, editor: Editor, element: PsiElement): RsWhereClause? {
        val whereClause = element.ancestorStrict<RsWhereClause>() ?: return null
        val wherePredList = whereClause.wherePredList
        if (wherePredList.isEmpty()) return null

        val typeParameterList = whereClause.ancestorStrict<RsGenericDeclaration>()?.typeParameterList ?: return null
        val lifetimes = typeParameterList.lifetimeParameterList
        val types = typeParameterList.typeParameterList
        if (wherePredList.any {
                it.lifetime?.reference?.resolve() !in lifetimes &&
                    (it.typeReference?.skipParens() as? RsPathType)?.path?.reference?.resolve() !in types
            }) return null
        return whereClause
    }

    override fun invoke(project: Project, editor: Editor, ctx: RsWhereClause) {
        val declaration = ctx.ancestorStrict<RsGenericDeclaration>() ?: return

        val typeParameterList = declaration.typeParameterList ?: return
        val generics = typeParameterList
            .getGenericParameters()
            .filter { it.name != null }
            .map { param ->
                when (param) {
                    is RsTypeParameter -> typeParameterText(param)
                    is RsLifetimeParameter -> lifetimeParameterText(param)
                    is RsConstParameter -> constParameterText(param)
                    else -> error("unreachable")
                }
            }

        val newElement = RsPsiFactory(project).createTypeParameterList(generics)
        val insertedParameterList = typeParameterList.replace(newElement)
        ctx.delete()
        editor.moveCaretToOffset(insertedParameterList, insertedParameterList.endOffset)
    }

    private fun typeParameterText(param: RsTypeParameter): String = buildString {
        append(param.name)
        val bounds = param.bounds.distinctBy { it.text }
        if (bounds.isNotEmpty()) {
            bounds.joinTo(this, separator = "+", prefix = ":") { it.text }
        }
        param.typeReference?.let { append("=${it.text}") }
    }

    private fun lifetimeParameterText(param: RsLifetimeParameter): String = buildString {
        append(param.name)
        val bounds = param.bounds.distinctBy { it.text }
        if (bounds.isNotEmpty()) {
            bounds.joinTo(this, separator = "+", prefix = ":") { it.text }
        }
    }

    private fun constParameterText(param: RsConstParameter): String = buildString {
        append("const ")
        append(param.name)
        param.typeReference?.let { append(": ${it.text}") }
    }
}
