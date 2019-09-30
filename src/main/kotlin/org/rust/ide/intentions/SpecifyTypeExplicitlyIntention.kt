/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.intentions

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.rust.ide.inspections.import.RsImportHelper.importTypeReferencesFromTy
import org.rust.ide.presentation.insertionSafeTextWithAliases
import org.rust.lang.core.psi.RsLetDecl
import org.rust.lang.core.psi.RsPatIdent
import org.rust.lang.core.psi.RsPsiFactory
import org.rust.lang.core.psi.ext.ancestorStrict
import org.rust.lang.core.types.infer.containsTyOfClass
import org.rust.lang.core.types.ty.Ty
import org.rust.lang.core.types.ty.TyAnon
import org.rust.lang.core.types.ty.TyInfer
import org.rust.lang.core.types.ty.TyUnknown
import org.rust.lang.core.types.type


class SpecifyTypeExplicitlyIntention : RsElementBaseIntentionAction<SpecifyTypeExplicitlyIntention.Context>() {
    override fun getFamilyName() = "Specify type explicitly"

    override fun getText() = "Specify type explicitly"

    override fun findApplicableContext(project: Project, editor: Editor, element: PsiElement): Context? {
        val letDecl = element.ancestorStrict<RsLetDecl>() ?: return null
        if (letDecl.typeReference != null) return null
        val ident = letDecl.pat as? RsPatIdent ?: return null
        val type = ident.patBinding.type
        if (type.containsTyOfClass(listOf(TyUnknown::class.java, TyInfer::class.java, TyAnon::class.java))) {
            return null
        }
        return Context(type, letDecl)
    }

    override fun invoke(project: Project, editor: Editor, ctx: Context) {
        val factory = RsPsiFactory(project)
        val createdType = factory.createType(ctx.type.insertionSafeTextWithAliases)
        val letDecl = ctx.letDecl
        val colon = letDecl.addAfter(factory.createColon(), letDecl.pat)
        letDecl.addAfter(createdType, colon)
        importTypeReferencesFromTy(ctx.letDecl, ctx.type, useAliases = true)
    }


    data class Context(
        val type: Ty,
        val letDecl: RsLetDecl
    )
}
