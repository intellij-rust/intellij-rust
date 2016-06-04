package org.rust.lang.core.type

import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.PsiModificationTracker
import org.rust.lang.core.psi.RustExprElement
import org.rust.lang.core.psi.RustTypeElement
import org.rust.lang.core.psi.visitors.RustTypificationVisitor
import org.rust.lang.core.type.unresolved.RustUnresolvedType
import org.rust.lang.core.type.visitors.RustTypeResolvingVisitor
import org.rust.lang.utils.psiCached

val RustExprElement.type: RustUnresolvedType by psiCached {
    CachedValueProvider {
        CachedValueProvider.Result.create(
            RustTypificationVisitor().let {
                accept(it)
                it.inferred
            },
            PsiModificationTracker.MODIFICATION_COUNT
        )
    }
}

val RustExprElement.resolvedType: RustType by psiCached {
    CachedValueProvider {
        CachedValueProvider.Result.create(type.accept(RustTypeResolvingVisitor()), PsiModificationTracker.MODIFICATION_COUNT)
    }
}

val RustTypeElement.type: RustUnresolvedType by psiCached {
    CachedValueProvider {
        CachedValueProvider.Result.create(
            RustTypificationVisitor().let {
                accept(it)
                it.inferred
            },
            PsiModificationTracker.MODIFICATION_COUNT
        )
    }
}

val RustTypeElement.resolvedType: RustType by psiCached {
    CachedValueProvider {
        CachedValueProvider.Result.create(type.accept(RustTypeResolvingVisitor()), PsiModificationTracker.MODIFICATION_COUNT)
    }
}

