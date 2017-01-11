package org.rust.ide.intentions

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.util.parentOfType

class MoveTypeConstraintToWhereClauseIntention : RustElementBaseIntentionAction<RustTypeParameterListElement>() {
    override fun getText() = "Move type constraint to where clause"
    override fun getFamilyName() = text

    override fun findApplicableContext(project: Project, editor: Editor, element: PsiElement): RustTypeParameterListElement? {
        val genericParams = element.parentOfType<RustTypeParameterListElement>() ?: return null
        val hasTypeBounds = genericParams.typeParamList.any { it.typeParamBounds != null }
        val hasLifetimeBounds = genericParams.lifetimeParamList.any { it.lifetimeParamBounds != null }
        return if (hasTypeBounds || hasLifetimeBounds) genericParams else null
    }

    override fun invoke(project: Project, editor: Editor, ctx: RustTypeParameterListElement) {
        val lifetimeBounds = ctx.lifetimeParamList
        val typeBounds = ctx.typeParamList
        val whereClause = RustPsiFactory(project).createWhereClause(lifetimeBounds, typeBounds)

        val declaration = ctx.parentOfType<RustGenericDeclaration>() ?: return
        val addedClause = declaration.addWhereClause(whereClause) ?: return
        val offset = addedClause.textOffset + whereClause.textLength
        editor.caretModel.moveToOffset(offset)
        typeBounds.forEach { it.typeParamBounds?.delete() }
        lifetimeBounds.forEach { it.lifetimeParamBounds?.delete() }
       }
}

private fun RustGenericDeclaration.addWhereClause(whereClause: RustWhereClauseElement): PsiElement? {
    val anchor = when (this) {
        is RustTypeAliasElement -> eq
        is RustImplItemElement -> lbrace
        is RustTraitItemElement -> lbrace
        is RustFunctionElement -> block
        is RustStructItemElement -> semicolon ?: blockFields
        is RustEnumItemElement -> enumBody
        else -> error("Unhandled RustGenericDeclaration: $this")
    } ?: return null

    return addBefore(whereClause, anchor)
}
