/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.inspections.lints

import com.intellij.psi.PsiElement
import org.rust.ide.inspections.RsProblemsHolder
import org.rust.ide.inspections.RsWithMacrosInspectionVisitor
import org.rust.lang.core.psi.RsFunction
import org.rust.lang.core.psi.RsVisitor
import org.rust.lang.core.psi.ext.*
import org.rust.lang.core.resolve.ImplLookup
import org.rust.lang.core.types.infer.selfType
import org.rust.lang.core.types.infer.typeOfValue
import org.rust.lang.core.types.selfType
import org.rust.lang.core.types.ty.TyReference
import org.rust.lang.core.types.ty.TyUnknown

class RsSelfConventionInspection : RsLintInspection() {

    override fun getLint(element: PsiElement): RsLint = RsLint.WrongSelfConvention

    override fun buildVisitor(holder: RsProblemsHolder, isOnTheFly: Boolean): RsVisitor =
        object : RsWithMacrosInspectionVisitor() {
            @Suppress("PARAMETER_NAME_CHANGED_ON_OVERRIDE")
            override fun visitFunction2(m: RsFunction) {
                val traitOrImpl = when (val owner = m.owner) {
                    is RsAbstractableOwner.Trait -> owner.trait
                    is RsAbstractableOwner.Impl -> owner.impl.takeIf { owner.isInherent }
                    else -> null
                } ?: return

                val convention = SELF_CONVENTIONS.find {
                    m.identifier.text.startsWith(it.prefix) &&
                    m.identifier.text.endsWith(it.postfix ?: "")
                } ?: return

                val selfSignature = m.selfSignature
                if (selfSignature in convention.selfSignatures) return

                // Ignore the inspection if the self type is arbitrary
                if (selfSignature is SelfSignature.ArbitrarySelfSignature &&
                    convention.selfSignatures != listOf(SelfSignature.NO_SELF)) {
                    return
                }

                if (selfSignature == SelfSignature.BY_VAL) {
                    val selfType = traitOrImpl.selfType
                    val implLookup = ImplLookup.relativeTo(traitOrImpl)
                    if (selfType is TyUnknown || implLookup.isCopy(selfType)) return
                }

                holder.registerProblem(m.selfParameter ?: m.identifier, convention)
            }
        }

    private companion object {
        val SELF_CONVENTIONS = listOf(
            SelfConvention("as_", listOf(SelfSignature.BY_REF, SelfSignature.BY_MUT_REF)),
            SelfConvention("from_", listOf(SelfSignature.NO_SELF)),
            SelfConvention("into_", listOf(SelfSignature.BY_VAL)),
            SelfConvention("is_", listOf(SelfSignature.BY_REF, SelfSignature.NO_SELF)),
            SelfConvention("to_", listOf(SelfSignature.BY_MUT_REF), postfix = "_mut"),
            SelfConvention("to_", listOf(SelfSignature.BY_REF, SelfSignature.BY_VAL)),
        )
    }
}

sealed class SelfSignature {
    object ArbitrarySelfSignature: SelfSignature()
    class BasicSelfSignature(val description: String): SelfSignature()

    companion object {
        val NO_SELF: BasicSelfSignature = BasicSelfSignature("no self")
        val BY_VAL: BasicSelfSignature = BasicSelfSignature("self by value")
        val BY_REF: BasicSelfSignature = BasicSelfSignature("self by reference")
        val BY_MUT_REF: BasicSelfSignature = BasicSelfSignature("self by mutable reference")
    }
}

private val RsFunction.selfSignature: SelfSignature
    get() {
        val self = selfParameter ?: return SelfSignature.NO_SELF

        // self: Self, self: &Self, self: Box<Self>, ...
        return if (self.isExplicitType) {
            val expectedSelfType = selfType ?: TyUnknown
            val actualSelfType = self.typeOfValue

            when {
                expectedSelfType.isEquivalentTo(actualSelfType) -> SelfSignature.BY_VAL
                actualSelfType is TyReference -> {
                    val mutable = actualSelfType.mutability.isMut
                    if (expectedSelfType.isEquivalentTo(actualSelfType.referenced)) {
                        if (mutable) {
                            SelfSignature.BY_MUT_REF
                        } else {
                            SelfSignature.BY_REF
                        }
                    } else {
                        null
                    }
                }
                else -> null
            } ?: SelfSignature.ArbitrarySelfSignature
        } else {
            when {
                self.isRef && self.mutability.isMut -> SelfSignature.BY_MUT_REF
                self.isRef -> SelfSignature.BY_REF
                else -> SelfSignature.BY_VAL
            }
        }
    }

data class SelfConvention(
    val prefix: String,
    val selfSignatures: Collection<SelfSignature.BasicSelfSignature>,
    val postfix: String? = null
)

private fun RsProblemsHolder.registerProblem(element: PsiElement, convention: SelfConvention) {
    val selfTypes = convention.selfSignatures.joinToString(" or ") { it.description }

    val description = "methods called `${convention.prefix}*${convention.postfix ?: ""}` usually take $selfTypes; " +
        "consider choosing a less ambiguous name"

    registerProblem(element, description)
}
