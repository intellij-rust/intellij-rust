/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.intentions

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.rust.ide.utils.import.RsImportHelper.importTypeReferencesFromTy
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.ancestorStrict
import org.rust.lang.core.psi.ext.arms
import org.rust.lang.core.psi.ext.variants
import org.rust.lang.core.types.ty.TyAdt
import org.rust.lang.core.types.ty.stripReferences
import org.rust.lang.core.types.type

class FillMatchArmsIntention : RsElementBaseIntentionAction<FillMatchArmsIntention.Context>() {

    override fun getText(): String = familyName
    override fun getFamilyName(): String = "Fill match arms"

    override fun findApplicableContext(project: Project, editor: Editor, element: PsiElement): Context? {
        val matchExpr = element.ancestorStrict<RsMatchExpr>() ?: return null
        if (matchExpr.arms.isNotEmpty()) return null
        val item = (matchExpr.expr?.type?.stripReferences() as? TyAdt)?.item as? RsEnumItem ?: return null
        // TODO: check enum variants can be used without enum name qualifier
        val name = item.name ?: return null
        return Context(matchExpr, name, item.variants)
    }

    override fun invoke(project: Project, editor: Editor, ctx: Context) {
        val (matchExpr, name, variants) = ctx

        val body = RsPsiFactory(project).createMatchBody(matchExpr, name, variants)
            .let { body ->
                val matchBody = matchExpr.matchBody
                if (matchBody != null) {
                    val rbrace = matchBody.rbrace
                    for (arm in body.matchArmList) {
                        matchBody.addBefore(arm, rbrace)
                    }
                    matchBody
                } else {
                    matchExpr.addAfter(body, matchExpr.expr) as RsMatchBody
                }
            }

        val lbraceOffset = (body.matchArmList.firstOrNull()?.expr as? RsBlockExpr)
            ?.block?.lbrace?.textOffset ?: return
        editor.caretModel.moveToOffset(lbraceOffset + 1)

        val ty = matchExpr.expr?.type ?: return
        importTypeReferencesFromTy(matchExpr, ty)
    }

    data class Context(
        val matchExpr: RsMatchExpr,
        val enumName: String,
        val variants: List<RsEnumVariant>
    )
}
