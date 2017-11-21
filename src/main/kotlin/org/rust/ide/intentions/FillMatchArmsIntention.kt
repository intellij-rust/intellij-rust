/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.intentions

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.util.containers.isNullOrEmpty
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.ancestorStrict
import org.rust.lang.core.resolve.StdKnownItems
import org.rust.lang.core.types.ty.TyEnum
import org.rust.lang.core.types.type

class FillMatchArmsIntention : RsElementBaseIntentionAction<FillMatchArmsIntention.Context>() {

    override fun getText(): String = familyName
    override fun getFamilyName(): String = "Fill match arms"

    override fun findApplicableContext(project: Project, editor: Editor, element: PsiElement): Context? {
        val matchExpr = element.ancestorStrict<RsMatchExpr>() ?: return null
        if (!matchExpr.matchBody?.matchArmList.isNullOrEmpty()) return null
        val type = matchExpr.expr?.type as? TyEnum ?: return null
        // TODO: check enum variants can be used without enum name qualifier
        val name = if (!isStdOptionOrResult(type.item)) {
            type.item.name ?: return null
        } else null
        val variants = type.item.enumBody?.enumVariantList ?: return null
        return Context(matchExpr, name, variants)
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

    private fun isStdOptionOrResult(element: RsEnumItem): Boolean {
        val knownItems = StdKnownItems.relativeTo(element)
        val option = knownItems.findOptionItem()
        val result = knownItems.findResultItem()
        return element == option || element == result
    }

    data class Context(
        val matchExpr: RsMatchExpr,
        val enumName: String?,
        val variants: List<RsEnumVariant>
    )
}
