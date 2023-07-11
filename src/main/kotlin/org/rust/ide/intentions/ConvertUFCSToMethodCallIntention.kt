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
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.*

class ConvertUFCSToMethodCallIntention : RsElementBaseIntentionAction<ConvertUFCSToMethodCallIntention.Context>() {
    override fun getText() = RsBundle.message("intention.name.convert.to.method.call")
    override fun getFamilyName() = text

    data class Context(
        val callExpr: RsCallExpr,
        val function: RsFunction
    )

    override fun findApplicableContext(project: Project, editor: Editor, element: PsiElement): Context? {
        val callExpr = element.ancestorStrict<RsCallExpr>(stopAt = RsValueArgumentList::class.java) ?: return null
        val path = (callExpr.expr as? RsPathExpr)?.path ?: return null
        val function = path.reference?.resolve() as? RsFunction ?: return null
        if (!function.isMethod) return null
        if (callExpr.valueArgumentList.exprList.isEmpty()) return null
        if (!PsiModificationUtil.canReplace(callExpr)) return null

        return Context(callExpr, function)
    }

    override fun invoke(project: Project, editor: Editor, ctx: Context) {
        val arguments = ctx.callExpr.valueArgumentList.exprList
        val selfArgument = arguments.getOrNull(0) ?: return

        val name = ctx.function.name ?: return
        val restArguments = arguments.drop(1)
        val methodCall = createMethodCall(ctx.function, selfArgument, name, restArguments) ?: return
        ctx.callExpr.replace(methodCall)
    }
}

private fun createMethodCall(
    function: RsFunction,
    selfArgument: RsExpr,
    name: String,
    restArguments: List<RsExpr>
): PsiElement? {
    val self = normalizeSelf(selfArgument, function)

    val factory = RsPsiFactory(selfArgument.project)
    val call = factory.tryCreateMethodCall(self, name, restArguments)
    if (call != null) return call

    val parenthesesExpr = factory.tryCreateExpression("(${self.text})") ?: return null
    return factory.tryCreateMethodCall(parenthesesExpr, name, restArguments)
}

private fun normalizeSelf(selfArgument: RsExpr, function: RsFunction): RsExpr {
    val isRef = function.selfParameter?.isRef ?: false
    if (!isRef) return selfArgument
    return selfArgument.unwrapReference()
}
