/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.intentions

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.rust.lang.core.psi.RsMethodCallExpr
import org.rust.lang.core.psi.RsPsiFactory
import org.rust.lang.core.psi.ext.parentOfType

class UnwrapToTryIntention : RsElementBaseIntentionAction<RsMethodCallExpr>() {
    override fun getText() = "Replace .unwrap() with try"
    override fun getFamilyName() = text

    override fun findApplicableContext(project: Project, editor: Editor, element: PsiElement): RsMethodCallExpr? {
        val methodCall = element
            .parentOfType<RsMethodCallExpr>(strict = false) ?: return null
        if (methodCall.identifier.textMatches("unwrap") &&
            methodCall.typeArgumentList == null &&
            methodCall.valueArgumentList.exprList.isEmpty()) {
            return methodCall
        }
        return null
    }

    override fun invoke(project: Project, editor: Editor, ctx: RsMethodCallExpr) {
        val tryElement = RsPsiFactory(project).createTryExpression(ctx.expr)
        ctx.replace(tryElement)
    }
}
