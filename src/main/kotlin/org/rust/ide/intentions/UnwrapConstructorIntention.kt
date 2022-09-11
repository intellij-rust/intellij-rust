/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.intentions

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.rust.RsBundle
import org.rust.lang.core.psi.RsCallExpr
import org.rust.lang.core.psi.RsExpr
import org.rust.lang.core.psi.RsPathExpr
import org.rust.lang.core.psi.ext.RsFieldsOwner
import org.rust.lang.core.psi.ext.ancestorStrict

class UnwrapConstructorIntention : RsElementBaseIntentionAction<Context>() {
    override fun getFamilyName() = RsBundle.message("intention.Rust.UnwrapConstructor.name")

    override fun findApplicableContext(project: Project, editor: Editor, element: PsiElement): Context? {
        val call = element.ancestorStrict<RsCallExpr>() ?: return null
        val argument = call.valueArgumentList.exprList.singleOrNull() ?: return null

        val expr = call.expr as? RsPathExpr ?: return null
        if (expr.path.reference?.resolve() !is RsFieldsOwner) return null

        text = RsBundle.message("intention.Rust.UnwrapConstructor.text", expr.text)

        return Context(call, argument)
    }

    override fun invoke(project: Project, editor: Editor, ctx: Context) {
        ctx.call.replace(ctx.argument)
    }
}

data class Context(val call: RsCallExpr, val argument: RsExpr)
