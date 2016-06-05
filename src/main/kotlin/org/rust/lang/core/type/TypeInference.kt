package org.rust.lang.core.type

import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiModificationTracker
import org.rust.lang.core.psi.RustExprElement
import org.rust.lang.core.psi.RustTypeElement
import org.rust.lang.core.psi.visitors.RustTypificationVisitor
import org.rust.lang.core.type.unresolved.RustUnresolvedType
import org.rust.lang.core.type.visitors.RustTypeResolvingVisitor

val RustExprElement.type: RustUnresolvedType
    get() =
        CachedValuesManager.getCachedValue(this,
            CachedValueProvider {
                CachedValueProvider.Result.create(
                    RustTypificationVisitor().let {
                        accept(it)
                        it.inferred
                    },
                    PsiModificationTracker.MODIFICATION_COUNT
                )
            }
        )

val RustExprElement.resolvedType: RustType
    get() =
        CachedValuesManager.getCachedValue(this,
            CachedValueProvider {
                CachedValueProvider.Result.create(type.accept(RustTypeResolvingVisitor()), PsiModificationTracker.MODIFICATION_COUNT)
            }
        )

val RustTypeElement.type: RustUnresolvedType
    get() =
        CachedValuesManager.getCachedValue(this,
            CachedValueProvider {
                CachedValueProvider.Result.create(
                    RustTypificationVisitor().let {
                        accept(it)
                        it.inferred
                    },
                    PsiModificationTracker.MODIFICATION_COUNT
                )
            }
        )

val RustTypeElement.resolvedType: RustType
    get() =
        CachedValuesManager.getCachedValue(this,
            CachedValueProvider {
                CachedValueProvider.Result.create(type.accept(RustTypeResolvingVisitor()), PsiModificationTracker.MODIFICATION_COUNT)
            }
        )

