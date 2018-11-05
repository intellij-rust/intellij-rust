package org.rust.ide.intentions

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.ancestorOrSelf
import org.rust.lang.core.psi.ext.parentDotExpr
import org.rust.lang.core.psi.ext.receiver

class UnwrapToMatchIntention: RsElementBaseIntentionAction<RsMethodCall>() {
    override fun getText() = "Replace .unwrap() with match"
    override fun getFamilyName() = text

    override fun findApplicableContext(project: Project, editor: Editor, element: PsiElement): RsMethodCall? {
        val methodCall = element.ancestorOrSelf<RsMethodCall>() ?: return null
        if (methodCall.referenceName == "unwrap" &&
            methodCall.typeArgumentList == null &&
            methodCall.valueArgumentList.exprList.isEmpty()) {
            return methodCall
        }
        return null
    }

    override fun invoke(project: Project, editor: Editor, ctx: RsMethodCall) {
        val generatedCode = buildString {
            append("match ")
            append(ctx.receiver.text)
            append(" {")
            append("Ok(x) => x,")
            append("Err(_) => unimplemented!(),")
            append("}")
        }

        val matchExpression = RsPsiFactory(project).createExpression(generatedCode) as RsMatchExpr
        ctx.parentDotExpr.replace(matchExpression)
    }
}
