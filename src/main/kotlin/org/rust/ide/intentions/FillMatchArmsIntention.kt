/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.intentions

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.ancestorStrict
import org.rust.lang.core.psi.ext.arms
import org.rust.lang.core.psi.ext.isStdOptionOrResult
import org.rust.lang.core.psi.ext.variants
import org.rust.lang.core.types.ty.TyAdt
import org.rust.lang.core.types.type

class FillMatchArmsIntention : RsElementBaseIntentionAction<FillMatchArmsIntention.Context>() {

    override fun getText(): String = familyName
    override fun getFamilyName(): String = "Fill match arms"

    override fun findApplicableContext(project: Project, editor: Editor, element: PsiElement): Context? {
        val matchExpr = element.ancestorStrict<RsMatchExpr>() ?: return null
        if (matchExpr.arms.isNotEmpty()) return null
        val item = (matchExpr.expr?.type as? TyAdt)?.item as? RsEnumItem ?: return null
        // TODO: check enum variants can be used without enum name qualifier
        val name = if (!item.isStdOptionOrResult) {
            item.name ?: return null
        } else null
        return Context(matchExpr, name, item.variants)
    }

    override fun invoke(project: Project, editor: Editor, ctx: Context) {
        val (expr, name, variants) = ctx
        var body = RsPsiFactory(project).createMatchBody(name, variants)
        val matchBody = expr.matchBody
        if (matchBody != null) {
            val rbrace = matchBody.rbrace
            for (arm in body.matchArmList){
                matchBody.addBefore(arm, rbrace)
            }
            body = matchBody
        } else {
            body = expr.addAfter(body, expr.expr) as RsMatchBody
        }

        val lbraceOffset = (body.matchArmList.firstOrNull()?.expr as? RsBlockExpr)
            ?.block?.lbrace?.textOffset ?: return
        editor.caretModel.moveToOffset(lbraceOffset + 1)
    }

    data class Context(
        val matchExpr: RsMatchExpr,
        val enumName: String?,
        val variants: List<RsEnumVariant>
    )
}
