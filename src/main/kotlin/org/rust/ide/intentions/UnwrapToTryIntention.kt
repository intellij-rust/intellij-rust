/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.intentions

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.rust.RsBundle
import org.rust.ide.utils.PsiModificationUtil
import org.rust.lang.core.psi.RsMethodCall
import org.rust.lang.core.psi.RsPsiFactory
import org.rust.lang.core.psi.ext.ancestorOrSelf
import org.rust.lang.core.psi.ext.parentDotExpr
import org.rust.lang.core.psi.ext.receiver

class UnwrapToTryIntention : RsElementBaseIntentionAction<RsMethodCall>() {
    override fun getText() = RsBundle.message("intention.name.replace.unwrap.with.try")
    override fun getFamilyName() = text

    override fun findApplicableContext(project: Project, editor: Editor, element: PsiElement): RsMethodCall? {
        val methodCall = element.ancestorOrSelf<RsMethodCall>() ?: return null
        val isAppropriateMethod = methodCall.referenceName == "unwrap"
            && methodCall.typeArgumentList == null
            && methodCall.valueArgumentList.exprList.isEmpty()
            && PsiModificationUtil.canReplace(methodCall.parentDotExpr)

        if (!isAppropriateMethod) return null

        return methodCall
    }

    override fun invoke(project: Project, editor: Editor, ctx: RsMethodCall) {
        val tryElement = RsPsiFactory(project).createTryExpression(ctx.receiver)
        ctx.parentDotExpr.replace(tryElement)
    }
}
