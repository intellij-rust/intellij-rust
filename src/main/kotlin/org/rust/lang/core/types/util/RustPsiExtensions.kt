package org.rust.lang.core.types.util

import com.intellij.openapi.util.Computable
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiModificationTracker
import org.rust.ide.utils.recursionGuard
import org.rust.lang.core.psi.*
import org.rust.lang.core.types.RustType
import org.rust.lang.core.types.RustUnknownType
import org.rust.lang.core.types.unresolved.RustUnresolvedType
import org.rust.lang.core.types.visitors.impl.RustTypeResolvingVisitor
import org.rust.lang.core.types.visitors.impl.RustTypificationEngine

val RustExprElement.resolvedType: RustType
    get() =
        CachedValuesManager.getCachedValue(this,
            CachedValueProvider {
                CachedValueProvider.Result.create(RustTypificationEngine.typifyExpr(this), PsiModificationTracker.MODIFICATION_COUNT)
            }
        )

val RustTypeElement.type: RustUnresolvedType
    get() =
        CachedValuesManager.getCachedValue(this,
            CachedValueProvider {
                CachedValueProvider.Result.create(
                    RustTypificationEngine.typifyType(this),
                    PsiModificationTracker.MODIFICATION_COUNT
                )
            }
        )

val RustTypeElement.resolvedType: RustType
    get() = recursionGuard(this, Computable {
        type.accept(RustTypeResolvingVisitor(this))
    }) ?: RustUnknownType

val RustTypeBearingItemElement.resolvedType: RustType
    get() =
        CachedValuesManager.getCachedValue(this,
            CachedValueProvider {
                CachedValueProvider.Result.create(RustTypificationEngine.typify(this), PsiModificationTracker.MODIFICATION_COUNT)
            })

/**
 * Helper property to extract (type-)bounds imposed onto this particular type-parameter
 */
val RustTypeParamElement.bounds: Sequence<RustPolyboundElement>
    get() {
        val owner = parent?.parent as? RustGenericDeclaration
        val whereBounds =
            owner?.whereClause?.wherePredList.orEmpty()
                .asSequence()
                .filter { (it.type as? RustBaseTypeElement)?.path?.reference?.resolve() == this }
                .flatMap    { it.typeParamBounds?.polyboundList.orEmpty().asSequence() }

        return typeParamBounds?.polyboundList.orEmpty().asSequence() + whereBounds
    }
