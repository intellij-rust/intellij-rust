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

class MoveTypeConstraintToWhereClauseIntention : RsElementBaseIntentionAction<RsTypeParameterList>() {
    override fun getText() = "Move type constraint to where clause"
    override fun getFamilyName() = text

    override fun findApplicableContext(project: Project, editor: Editor, element: PsiElement): RsTypeParameterList? {
        val genericParams = element.ancestorStrict<RsTypeParameterList>() ?: return null
        val hasTypeBounds = genericParams.typeParameterList.any { it.typeParamBounds != null }
        val hasLifetimeBounds = genericParams.lifetimeParameterList.any { it.lifetimeParamBounds != null }
        return if (hasTypeBounds || hasLifetimeBounds) genericParams else null
    }

    override fun invoke(project: Project, editor: Editor, ctx: RsTypeParameterList) {
        val lifetimeBounds = ctx.lifetimeParameterList
        val typeBounds = ctx.typeParameterList
        val whereClause = RsPsiFactory(project).createWhereClause(lifetimeBounds, typeBounds)

        val declaration = ctx.ancestorStrict<RsGenericDeclaration>() ?: return
        val addedClause = declaration.addWhereClause(whereClause) ?: return
        val offset = addedClause.textOffset + whereClause.textLength
        editor.caretModel.moveToOffset(offset)
        typeBounds.forEach { it.typeParamBounds?.delete() }
        lifetimeBounds.forEach { it.lifetimeParamBounds?.delete() }
    }
}

private fun RsGenericDeclaration.addWhereClause(whereClause: RsWhereClause): PsiElement? {
    val existingWhereClause = this.whereClause
    if (existingWhereClause != null) {
        ensureTrailingComma(existingWhereClause.wherePredList)
        existingWhereClause.addRangeAfter(
            whereClause.wherePredList.first(),
            whereClause.lastChild,
            existingWhereClause.lastChild
        )
        return existingWhereClause
    }

    val anchor = when (this) {
        is RsTypeAlias -> eq
        is RsTraitOrImpl -> members
        is RsFunction -> block
        is RsStructItem -> semicolon ?: blockFields
        is RsEnumItem -> enumBody
        else -> error("Unhandled RustGenericDeclaration: $this")
    } ?: return null

    return addBefore(whereClause, anchor)
}
