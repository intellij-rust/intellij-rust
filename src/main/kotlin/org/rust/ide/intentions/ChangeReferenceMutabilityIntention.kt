/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.intentions

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.rust.RsBundle
import org.rust.ide.intentions.util.macros.InvokeInside
import org.rust.ide.utils.PsiModificationUtil
import org.rust.lang.core.psi.RsPsiFactory
import org.rust.lang.core.psi.RsRefLikeType
import org.rust.lang.core.psi.RsTypeReference
import org.rust.lang.core.psi.ext.ancestorStrict
import org.rust.lang.core.psi.ext.isRef
import org.rust.lang.core.psi.ext.mutability
import org.rust.lang.core.types.ty.Mutability

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
class SetMutableIntention : ChangeReferenceMutabilityIntention() {
    override fun getText() = RsBundle.message("intention.name.set.reference.mutable")
    override val newMutability: Mutability get() = Mutability.MUTABLE
}

/**
 * Set reference immutable
 *
 * ```
 * &mut type
 * ```
 *
 * to this:
 *
 * ```
 * &type
 * ```
 */
class SetImmutableIntention : ChangeReferenceMutabilityIntention() {
    override fun getText() = RsBundle.message("intention.name.set.reference.immutable")
    override val newMutability: Mutability get() = Mutability.IMMUTABLE
}

abstract class ChangeReferenceMutabilityIntention : RsElementBaseIntentionAction<ChangeReferenceMutabilityIntention.Context>() {
    override fun getFamilyName() = text

    override val attributeMacroHandlingStrategy: InvokeInside get() = InvokeInside.MACRO_CALL

    protected abstract val newMutability: Mutability

    data class Context(
        val refType: RsRefLikeType,
        val referencedType: RsTypeReference
    )

    override fun findApplicableContext(project: Project, editor: Editor, element: PsiElement): Context? {
        val refType = element.ancestorStrict<RsRefLikeType>() ?: return null
        if (!refType.isRef) return null
        val referencedType = refType.typeReference ?: return null

        if (refType.mutability == newMutability) return null
        if (!PsiModificationUtil.canReplace(refType)) return null

        return Context(refType, referencedType)
    }

    override fun invoke(project: Project, editor: Editor, ctx: Context) {
        val newType = RsPsiFactory(project).createReferenceType(ctx.referencedType.text, newMutability)
        ctx.refType.replace(newType)
    }
}
