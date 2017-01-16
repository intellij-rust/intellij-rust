package org.rust.lang.core.types.util

import com.intellij.openapi.util.Computable
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiModificationTracker
import org.rust.ide.utils.recursionGuard
import org.rust.lang.core.psi.RsExpr
import org.rust.lang.core.psi.RsType
import org.rust.lang.core.psi.RsTypeBearingItemElement
import org.rust.lang.core.types.RustType
import org.rust.lang.core.types.RustUnknownType
import org.rust.lang.core.types.visitors.impl.RustTypificationEngine

val RsExpr.resolvedType: RustType
    get() =
    CachedValuesManager.getCachedValue(this,
        CachedValueProvider {
            CachedValueProvider.Result.create(RustTypificationEngine.typifyExpr(this), PsiModificationTracker.MODIFICATION_COUNT)
        }
    )

val RsType.resolvedType: RustType
    get() = recursionGuard(this, Computable {
        RustTypificationEngine.typifyType(this)
    }) ?: RustUnknownType

val RsTypeBearingItemElement.resolvedType: RustType
    get() =
    CachedValuesManager.getCachedValue(this,
        CachedValueProvider {
            CachedValueProvider.Result.create(RustTypificationEngine.typify(this), PsiModificationTracker.MODIFICATION_COUNT)
        })
