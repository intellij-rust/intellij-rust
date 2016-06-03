package org.rust.lang.core.type

import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.util.parentOfType
import org.rust.lang.utils.psiCached

val RustExprElement.inferredType: RustResolvedType by psiCached {
    when (this) {
        is RustPathExprElement -> {
            val target = path.reference.resolve()
            when (target) {
                is RustSelfArgumentElement -> target.parentOfType<RustImplItemElement>()?.type?.resolvedType ?: RustUnknownType
                else -> RustUnknownType
            }
        }
        else -> RustUnknownType
    }
}

val RustTypeElement.resolvedType: RustResolvedType by psiCached {
    when (this) {
        is RustPathTypeElement -> {
            val target = path?.reference?.resolve()
            when (target) {
                is RustStructItemElement -> RustStructType(target)
                else -> RustUnknownType
            }
        }
        else -> RustUnknownType
    }
}

