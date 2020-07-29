/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.intentions

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.rust.ide.presentation.renderInsertionSafe
import org.rust.ide.utils.import.RsImportHelper.importTypeReferencesFromTy
import org.rust.lang.core.psi.RsLetDecl
import org.rust.lang.core.psi.RsPatIdent
import org.rust.lang.core.psi.RsPsiFactory
import org.rust.lang.core.psi.ext.RsBindingModeKind.BindByReference
import org.rust.lang.core.psi.ext.ancestorStrict
import org.rust.lang.core.psi.ext.kind
import org.rust.lang.core.psi.ext.startOffset
import org.rust.lang.core.types.consts.CtInferVar
import org.rust.lang.core.types.consts.CtUnevaluated
import org.rust.lang.core.types.consts.CtUnknown
import org.rust.lang.core.types.infer.containsConstOfClass
import org.rust.lang.core.types.infer.containsTyOfClass
import org.rust.lang.core.types.ty.*
import org.rust.lang.core.types.type


class SpecifyTypeExplicitlyIntention : RsElementBaseIntentionAction<SpecifyTypeExplicitlyIntention.Context>() {
    override fun getFamilyName() = "Specify type explicitly"

    override fun getText() = "Specify type explicitly"

    override fun findApplicableContext(project: Project, editor: Editor, element: PsiElement): Context? {
        val letDecl = element.ancestorStrict<RsLetDecl>() ?: return null
        if (letDecl.typeReference != null) return null
        val initializer = letDecl.expr
        if (initializer != null && element.startOffset >= initializer.startOffset - 1) return null
        val pat = letDecl.pat ?: return null
        val patType = pat.type
        if (patType.containsTyOfClass(TyUnknown::class.java, TyInfer::class.java, TyAnon::class.java)
            || patType.containsConstOfClass(CtUnknown::class.java, CtInferVar::class.java, CtUnevaluated::class.java)) {
            return null
        }

        // let ref x = 1; // `i32` should be inserted instead of `&i32`
        val type = if (pat is RsPatIdent && pat.patBinding.kind is BindByReference && patType is TyReference) {
            patType.referenced
        } else {
            patType
        }

        return Context(type, letDecl)
    }

    override fun invoke(project: Project, editor: Editor, ctx: Context) {
        val factory = RsPsiFactory(project)
        val createdType = factory.createType(ctx.type.renderInsertionSafe(useAliasNames = true))
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
