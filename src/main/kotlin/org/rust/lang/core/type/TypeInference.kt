package org.rust.lang.core.type

import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.util.parentOfType
import org.rust.lang.utils.psiCached

val RustExpr.inferredType: RustResolvedType by psiCached {
    when (this) {
        is RustPathExpr -> {
            val target = path.reference.resolve()
            when (target) {
                is RustSelfArgument -> target.parentOfType<RustImplItem>()?.type?.resolvedType ?: RustUnknownType
                else -> RustUnknownType
            }
        }
        else -> RustUnknownType
    }
}

val RustType.resolvedType: RustResolvedType by psiCached {
    when (this) {
        is RustPathType -> {
            val target = path?.reference?.resolve()
            when (target) {
                is RustStructItem -> RustStructType(target)
                else -> RustUnknownType
            }
        }
        else -> RustUnknownType
    }
}

