/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.intentions

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.rust.lang.core.psi.RsBaseType
import org.rust.lang.core.psi.RsPsiFactory
import org.rust.lang.core.psi.RsRefLikeType
import org.rust.lang.core.psi.ext.isRef
import org.rust.lang.core.psi.ext.mutability
import org.rust.lang.core.psi.ext.ancestorStrict
import org.rust.lang.core.psi.ext.typeElement

/**
 * Set reference mutable
 *
 * ```
 * &type
 * ```
 *
 * to this:
 *
 * ```
 * &mut type
 * ```
 */
open class SetMutableIntention : RsElementBaseIntentionAction<SetMutableIntention.Context>() {
    override fun getText() = "Set reference mutable"
    override fun getFamilyName() = text

    open val mutable = true

    data class Context(
        val refType: RsRefLikeType,
        val baseType: RsBaseType
    )

    override fun findApplicableContext(project: Project, editor: Editor, element: PsiElement): Context? {
        val refType = element.ancestorStrict<RsRefLikeType>() ?: return null
        if (!refType.isRef) return null
        val baseType = refType.typeReference.typeElement as? RsBaseType ?: return null
        if (refType.mutability.isMut == mutable) return null
        return Context(refType, baseType)

    }

    override fun invoke(project: Project, editor: Editor, ctx: Context) {
        val newType = RsPsiFactory(project).createReferenceType(ctx.baseType.text, mutable)
        ctx.refType.replace(newType)
    }
}
