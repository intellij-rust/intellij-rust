package org.rust.lang.core.types.util

import com.intellij.openapi.util.Computable
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiModificationTracker
import org.rust.ide.utils.recursionGuard
import org.rust.lang.core.psi.RustExprElement
import org.rust.lang.core.psi.RustTypeBearingItemElement
import org.rust.lang.core.psi.RustTypeElement
import org.rust.lang.core.types.RustType
import org.rust.lang.core.types.RustUnknownType
import org.rust.lang.core.types.unresolved.RustUnresolvedType
import org.rust.lang.core.types.visitors.RustTypeResolvingVisitor
import org.rust.lang.core.types.visitors.RustTypificationEngine

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
    get() =
        CachedValuesManager.getCachedValue(this,
            CachedValueProvider {
                val result = recursionGuard(this,  Computable {
                   type.accept(RustTypeResolvingVisitor())
                }) ?: RustUnknownType
                CachedValueProvider.Result.create(result, PsiModificationTracker.MODIFICATION_COUNT)
            }
        )

val RustTypeBearingItemElement.resolvedType: RustType
    get() =
        CachedValuesManager.getCachedValue(this,
            CachedValueProvider {
                CachedValueProvider.Result.create(RustTypificationEngine.typify(this), PsiModificationTracker.MODIFICATION_COUNT)
            })

