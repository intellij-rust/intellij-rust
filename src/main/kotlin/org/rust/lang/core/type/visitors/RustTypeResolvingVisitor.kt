package org.rust.lang.core.type.visitors

import org.rust.lang.core.psi.RustImplItemElement
import org.rust.lang.core.psi.RustSelfArgumentElement
import org.rust.lang.core.psi.RustStructItemElement
import org.rust.lang.core.psi.RustTraitItemElement
import org.rust.lang.core.psi.util.parentOfType
import org.rust.lang.core.type.*
import org.rust.lang.core.type.unresolved.RustUnresolvedPathType
import org.rust.lang.core.type.unresolved.RustUnresolvedType

open class RustTypeResolvingVisitor : RustUnresolvedTypeVisitor<RustType> {

    private fun visit(type: RustUnresolvedType): RustType = type.accept(this)

    override fun visit(type: RustUnresolvedPathType): RustType {
        type.path.reference.resolve().let {
            return when (it) {
                is RustStructItemElement -> RustStructType(it)

                is RustSelfArgumentElement -> deviseSelfType(it)

                else -> RustUnknownType

            }
        }
    }

    /**
     * Devises type for the given (implicit) self-argument
     */
    private fun deviseSelfType(self: RustSelfArgumentElement): RustType =
        self.parentOfType<RustImplItemElement>()?.let { impl ->
            val type  = impl.type
            val trait = impl.traitRef

            if (type != null && trait != null)
                trait.path.reference.resolve()
                    .let { RustTraitImplType(it as RustTraitItemElement, type.resolvedType) }
            else
                type?.resolvedType
        } ?: RustUnknownType
}

