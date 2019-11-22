/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.types.borrowck

import org.rust.lang.core.psi.RsPatBinding
import org.rust.lang.core.psi.ext.RsElement
import org.rust.lang.core.types.borrowck.LoanPathElement.Deref
import org.rust.lang.core.types.borrowck.LoanPathKind.*
import org.rust.lang.core.types.infer.Categorization
import org.rust.lang.core.types.infer.Cmt
import org.rust.lang.core.types.infer.MutabilityCategory
import org.rust.lang.core.types.infer.PointerKind
import org.rust.lang.core.types.regions.Scope
import org.rust.lang.core.types.ty.Ty
import java.util.*

data class LoanPath(val kind: LoanPathKind, val ty: Ty, val element: RsElement) {
    override fun equals(other: Any?): Boolean =
        Objects.equals(this.kind, (other as? LoanPath)?.kind)

    override fun hashCode(): Int =
        kind.hashCode()

    fun killScope(bccx: BorrowCheckContext): Scope =
        when (kind) {
            is Var -> {
                val variable = kind.declaration
                if (variable is RsPatBinding) {
                    bccx.cfg.regionScopeTree.getVariableScope(variable) ?: Scope.Node(variable)
                } else {
                    Scope.Node(variable)
                }
            }
            is Downcast -> kind.loanPath.killScope(bccx)
            is Extend -> kind.loanPath.killScope(bccx)
        }

    companion object {
        fun computeFor(cmt: Cmt): LoanPath? {
            fun loanPath(kind: LoanPathKind): LoanPath = LoanPath(kind, cmt.ty, cmt.element)

            return when (val category = cmt.category) {
                is Categorization.Rvalue, Categorization.StaticItem -> null

                is Categorization.Local -> loanPath(Var(category.declaration))

                is Categorization.Deref -> {
                    val baseLp = computeFor(category.cmt) ?: return null
                    loanPath(Extend(baseLp, cmt.mutabilityCategory, Deref(category.pointerKind)))
                }

                is Categorization.Interior -> {
                    val baseCmt = category.cmt
                    val baseLp = computeFor(baseCmt) ?: return null
                    val interiorElement = (baseCmt.category as? Categorization.Downcast)?.element
                    val lpElement = LoanPathElement.Interior.fromCategory(category, interiorElement)
                    val kind = Extend(baseLp, cmt.mutabilityCategory, lpElement)
                    loanPath(kind)
                }

                is Categorization.Downcast -> {
                    val baseLp = computeFor(category.cmt) ?: return null
                    loanPath(Downcast(baseLp, category.element))
                }

                null -> null
            }
        }
    }
}

sealed class LoanPathKind {
    /** [Var] kind relates to [Categorization.Local] memory category */
    data class Var(val declaration: RsElement) : LoanPathKind()

    /** [Downcast] kind relates to [Categorization.Downcast] memory category */
    data class Downcast(val loanPath: LoanPath, val element: RsElement) : LoanPathKind()

    /** [Extend] kind relates to [[Categorization.Deref] and [Categorization.Interior] memory categories */
    data class Extend(val loanPath: LoanPath, val mutCategory: MutabilityCategory, val lpElement: LoanPathElement) : LoanPathKind()
}

sealed class LoanPathElement {
    data class Deref(val kind: PointerKind) : LoanPathElement()

    sealed class Interior : LoanPathElement() {
        abstract val element: RsElement?

        data class Field(override val element: RsElement?, val name: String?) : Interior()
        data class Index(override val element: RsElement?) : Interior()
        data class Pattern(override val element: RsElement?) : Interior()

        companion object {
            fun fromCategory(category: Categorization.Interior, element: RsElement?): LoanPathElement.Interior =
                when (category) {
                    is Categorization.Interior.Field -> Interior.Field(element, category.name)
                    is Categorization.Interior.Index -> Interior.Index(element)
                    is Categorization.Interior.Pattern -> Interior.Pattern(element)
                }
        }
    }
}
