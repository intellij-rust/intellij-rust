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

class ConvertUFCSToMethodCallIntention : RsElementBaseIntentionAction<ConvertUFCSToMethodCallIntention.Context>() {
    override fun getText() = "Convert to method call"
    override fun getFamilyName() = text

    data class Context(val callExpr: RsCallExpr, val function: RsFunction)

    override fun findApplicableContext(project: Project, editor: Editor, element: PsiElement): Context? {
        val call = element.ancestorStrict<RsCallExpr>(RsValueArgumentList::class.java) ?: return null
        val path = (call.expr as? RsPathExpr)?.path ?: return null
        val function = path.reference?.resolve() as? RsFunction ?: return null
        if (!function.isMethod) return null
        if (call.valueArgumentList.exprList.isEmpty()) return null

        return Context(call, function)
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

fun normalizeSelf(selfArgument: RsExpr, function: RsFunction): RsExpr {
    val isRef = function.selfParameter?.isRef ?: false
    val normalized = when {
        isRef && selfArgument is RsUnaryExpr && selfArgument.operatorType in REF_OPERATORS -> selfArgument.expr
        else -> null
    }
    return normalized ?: selfArgument
}

private val REF_OPERATORS = setOf(UnaryOperator.REF, UnaryOperator.REF_MUT)
