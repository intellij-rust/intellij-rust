/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.intentions

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.rust.RsBundle
import org.rust.ide.presentation.renderInsertionSafe
import org.rust.ide.utils.PsiInsertionPlace
import org.rust.ide.utils.import.RsImportHelper
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
    override fun getFamilyName() = RsBundle.message("intention.name.specify.type.explicitly")
    override fun getText() = RsBundle.message("intention.name.specify.type.explicitly")

    data class Context(
        val type: Ty,
        val letDecl: RsLetDecl,
        val place: PsiInsertionPlace,
    )

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

        val place = PsiInsertionPlace.after(pat) ?: return null

        return Context(type, letDecl, place)
    }

    override fun invoke(project: Project, editor: Editor, ctx: Context) {
        val factory = RsPsiFactory(project)
        val letDecl = ctx.letDecl
        val (toImport, toQualify) = RsImportHelper.getTypeReferencesInfoFromTys(letDecl, ctx.type)

        val createdType = factory.createType(ctx.type.renderInsertionSafe(letDecl, useQualifiedName = toQualify))

        ctx.place.insertMultiple(factory.createColon(), createdType)

        RsImportHelper.importElements(letDecl, toImport)
    }
}
