package org.rust.lang.utils

import com.intellij.openapi.Disposable
import com.intellij.psi.PsiElement
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiModificationTracker
import kotlin.reflect.KProperty


/**
 * Helper disposing [d] upon completing the execution of the [block]
 *
 * @d       Target `Disposable` to be disposed upon completion of the @block
 * @block   Target block to be run prior to disposal of @d
 */
fun <T> using(d: Disposable, block: () -> T): T {
    try {
        return block()
    } finally {
        d.dispose()
    }
}

/**
 * Helper disposing [d] upon completing the execution of the [block] (under the [d])
 *
 * @d       Target `Disposable` to be disposed upon completion of the @block
 * @block   Target block to be run prior to disposal of @d
 */
fun <D: Disposable, T> usingWith(d: D, block: (D) -> T): T {
    try {
        return block(d)
    } finally {
        d.dispose()
    }
}

/**
 * Cached value invalidated on any PSI modification
 */
fun<E : PsiElement, T> psiCached(block: E.() -> T): PsiCacheDelegate<E, T> = PsiCacheDelegate(block)

class PsiCacheDelegate<E : PsiElement, T>(val block: E.() -> T) {
    operator fun getValue(element: E, property: KProperty<*>): T {
        return CachedValuesManager.getCachedValue(element, CachedValueProvider {
            CachedValueProvider.Result.create(element.block(), PsiModificationTracker.MODIFICATION_COUNT)
        })
    }

}
