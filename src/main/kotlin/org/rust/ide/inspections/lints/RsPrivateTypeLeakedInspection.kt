/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.inspections.lints

import com.intellij.psi.PsiElement
import com.intellij.psi.util.parentOfType
import org.rust.ide.inspections.RsProblemsHolder
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.*
import org.rust.lang.core.types.ty.TyAdt
import org.rust.lang.core.types.type
import org.rust.lang.utils.RsDiagnostic
import org.rust.lang.utils.Severity
import org.rust.lang.utils.addToHolder

class RsPrivateTypeLeakedInspection : RsLintInspection() {
    override fun getLint(element: PsiElement): RsLint = RsLint.PrivateTypeInPublicInterface

    override fun buildVisitor(holder: RsProblemsHolder, isOnTheFly: Boolean): RsVisitor =
        object : RsVisitor() {
            override fun visitTypeReference(o: RsTypeReference) {
                val ctx = getContext(o)
                if (ctx != null) {
                    val target = when (val type = o.type) {
                        is TyAdt -> type.item
                        else -> null
                    }
                    if (target != null) {
                        checkTypeLeakage(holder, ctx, o, listOf(target))
                    }
                }

                super.visitTypeReference(o)
            }

            override fun visitTraitRef(o: RsTraitRef) {
                val ctx = getContext(o)
                if (ctx != null) {
                    val trait = o.resolveToTrait()
                    if (trait != null) {
                        checkTypeLeakage(holder, ctx, o, listOf(trait))
                    }
                }

                super.visitTraitRef(o)
            }
        }
}

private data class Context(
    val owner: RsItemElement,
    val severity: Severity
)

private fun getContext(element: PsiElement): Context? {
    val owner = element.parentOfType<RsItemElement>() ?: return null
    if (owner !is RsAbstractable &&
        owner !is RsStructOrEnumItemElement &&
        owner !is RsTraitItem) return null

    // Ignore private fields
    val fieldParent = element.parentOfType<RsFieldDecl>()
    if (fieldParent?.visibility == RsVisibility.Private) return null

    // Ignore types inside functions
    if (owner is RsFunction && owner.block?.isAncestorOf(element) == true) return null

    val (realOwner, severity) = when {
        owner is RsEnumVariant && owner.blockFields?.isAncestorOf(element) == true -> owner to Severity.WARN
        owner is RsAbstractable && owner.owner is RsAbstractableOwner.Trait ->
            (owner.owner as RsAbstractableOwner.Trait).trait to Severity.WARN
        owner is RsTypeAlias -> owner to Severity.WARN
        else -> owner to Severity.ERROR
    }

    if (realOwner.visibility == RsVisibility.Private) return null
    if (realOwner.parent !is RsMod) return null

    return Context(realOwner, severity)
}

private fun checkTypeLeakage(
    holder: RsProblemsHolder,
    context: Context,
    reference: RsElement,
    targets: List<RsVisibilityOwner>
) {
    // This will produce E0603
    if (targets.any { !it.isVisibleFrom(reference.containingMod) }) return

    for (target in targets) {
        val isLeaked = when (val visibility = context.owner.visibility) {
            is RsVisibility.Public -> target.visibility != RsVisibility.Public
            is RsVisibility.Restricted -> !target.isVisibleFrom(visibility.inMod)
            else -> false
        }
        if (isLeaked) {
            // TODO: handle error/warning disparity in lint inspections
            RsDiagnostic.PrivateTypeLeaked(
                Severity.ERROR,
                target,
                reference,
                (target as? RsNamedElement)?.name ?: error("Missing name")
            )
            .addToHolder(holder)
        }
    }
}
