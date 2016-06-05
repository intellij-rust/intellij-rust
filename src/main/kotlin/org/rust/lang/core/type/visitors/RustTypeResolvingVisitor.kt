package org.rust.lang.core.type.visitors

import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.util.parentOfType
import org.rust.lang.core.type.*
import org.rust.lang.core.type.unresolved.RustUnresolvedPathType
import org.rust.lang.core.type.unresolved.RustUnresolvedType
import org.rust.lang.core.type.util.resolvedType

open class RustTypeResolvingVisitor : RustUnresolvedTypeVisitor<RustType> {

    private fun visit(type: RustUnresolvedType): RustType = type.accept(this)

    override fun visit(type: RustUnresolvedPathType): RustType {
        type.path.reference.resolve().let {
            return when (it) {
                is RustStructItemElement -> RustStructType(it)

                is RustSelfArgumentElement -> deviseSelfType(it)

                is RustPatBindingElement -> deviseBoundPatType(it)

                else -> RustUnknownType

            }
        }
    }

    /**
     * NOTA BENE: That's far from complete
     */
    private fun deviseBoundPatType(pat: RustPatBindingElement): RustType {
        val letDecl = pat.parentOfType<RustLetDeclElement>()
        if (letDecl != null) {
            letDecl.type?.let { return it.resolvedType }
        }

        return RustUnknownType
    }

    /**
     * Devises type for the given (implicit) self-argument
     */
    private fun deviseSelfType(self: RustSelfArgumentElement): RustType =
        self.parentOfType<RustImplItemElement>()?.type?.resolvedType ?: RustUnknownType
}

