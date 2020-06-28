/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.intentions

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.rust.ide.refactoring.introduceVariable.extractExpression
import org.rust.lang.core.psi.RsBlock
import org.rust.lang.core.psi.RsExpr
import org.rust.lang.core.psi.RsExprStmt
import org.rust.lang.core.psi.RsRetExpr
import org.rust.lang.core.psi.ext.ancestors
import org.rust.lang.core.types.ty.TyUnit
import org.rust.lang.core.types.type

class IntroduceLocalVariableIntention : RsElementBaseIntentionAction<RsExpr>() {
    override fun getText(): String = "Introduce local variable"
    override fun getFamilyName(): String = text

    override fun findApplicableContext(project: Project, editor: Editor, element: PsiElement): RsExpr? {
        val expr = element.ancestors
            .takeWhile { it !is RsBlock }
            .find {
                val parent = it.parent
                parent is RsRetExpr || parent is RsExprStmt || parent is RsBlock && it == parent.expr
            } as? RsExpr

        if (expr?.type == TyUnit) return null

        return expr
    }

    override fun invoke(project: Project, editor: Editor, ctx: RsExpr) {
        extractExpression(editor, ctx)
    }
}
