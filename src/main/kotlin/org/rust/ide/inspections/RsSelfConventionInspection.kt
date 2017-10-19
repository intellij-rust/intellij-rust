/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.inspections

import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElement
import org.rust.lang.core.psi.RsFunction
import org.rust.lang.core.psi.RsImplItem
import org.rust.lang.core.psi.RsTraitItem
import org.rust.lang.core.psi.RsVisitor
import org.rust.lang.core.psi.ext.*
import org.rust.lang.core.resolve.ImplLookup
import org.rust.lang.core.types.ty.TyStructOrEnumBase
import org.rust.lang.core.types.type

class RsSelfConventionInspection : RsLocalInspectionTool() {

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean) =
        object : RsVisitor() {
            override fun visitFunction(m: RsFunction) {
                val owner = m.owner
                if (!(owner.isInherentImpl || owner.isTrait)) return

                val convention = SELF_CONVENTIONS.find { m.identifier.text.startsWith(it.prefix) } ?: return
                if (m.selfSignature in convention.selfSignatures) return
                if (m.selfSignature == SelfSignature.BY_VAL && owner.isOwnerCopyable()) return
                holder.registerProblem(m.selfParameter ?: m.identifier, convention)
            }
        }

    private fun RsFunctionOwner.isOwnerCopyable(): Boolean {
        val impls = when (this) {
            is RsFunctionOwner.Trait -> trait.traits
            is RsFunctionOwner.Impl -> impl.traits
            else -> null
        } ?: return false
        return impls.any { it.name == "Copy" }
    }

    private companion object {
        val SELF_CONVENTIONS = listOf(
            SelfConvention("as_", listOf(SelfSignature.BY_REF, SelfSignature.BY_MUT_REF)),
            SelfConvention("from_", listOf(SelfSignature.NO_SELF)),
            SelfConvention("into_", listOf(SelfSignature.BY_VAL)),
            SelfConvention("is_", listOf(SelfSignature.BY_REF, SelfSignature.NO_SELF)),
            SelfConvention("to_", listOf(SelfSignature.BY_REF))
        )
    }
}

enum class SelfSignature(val description: String) {
    NO_SELF("no self"),
    BY_VAL("self by value"),
    BY_REF("self by reference"),
    BY_MUT_REF("self by mutable reference");
}

private val RsTraitOrImpl.traits: Sequence<RsTraitItem>
    get() {
        when (this) {
            is RsImplItem -> {
                val type = this.typeReference?.type as? TyStructOrEnumBase ?: return emptySequence()
                val impls = ImplLookup.relativeTo(type.item)
                    .findImplsAndTraits(type)
                return impls.asSequence().mapNotNull { it.element as? RsTraitItem }
            }
            is RsTraitItem -> return superTraits.map { it.element }
        }
        return emptySequence()
    }

private val RsFunction.selfSignature: SelfSignature
    get() {
        val self = selfParameter
        return when {
            self == null -> SelfSignature.NO_SELF
            self.isRef && self.mutability.isMut -> SelfSignature.BY_MUT_REF
            self.isRef -> SelfSignature.BY_REF
            else -> SelfSignature.BY_VAL
        }
    }

data class SelfConvention(val prefix: String, val selfSignatures: Collection<SelfSignature>)

private fun ProblemsHolder.registerProblem(element: PsiElement, convention: SelfConvention) {
    val selfTypes = convention.selfSignatures.joinToString(" or ") { it.description }

    val description = "methods called `${convention.prefix}*` usually take $selfTypes; " +
        "consider choosing a less ambiguous name"

    registerProblem(element, description)
}
