/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.psi.ext

import com.intellij.injected.editor.VirtualFileWindow
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.PsiModificationTracker
import org.rust.lang.core.psi.*

/**
 * PSI element that implements this interface holds type inference context that
 * can be retrieved for each child element by [org.rust.lang.core.types.inference]
 *
 * @see org.rust.lang.core.types.infer.RsInferenceContext.infer
 */
interface RsInferenceContextOwner : RsElement

val RsInferenceContextOwner.body: RsElement?
    get() = when (this) {
        is RsArrayType -> expr
        is RsConstant -> expr
        is RsConstParameter -> expr
        is RsFunction -> block
        is RsVariantDiscriminant -> expr
        is RsExpressionCodeFragment -> expr
        is RsReplCodeFragment -> this
        is RsPathCodeFragment -> this
        is RsPath -> typeArgumentList
        else -> null
    }

fun <T> RsInferenceContextOwner.createCachedResult(value: T): CachedValueProvider.Result<T> {
    val structureModificationTracker = project.rustStructureModificationTracker

    return when {
        // The case of injected language. Injected PSI don't have its own event system, so can only
        // handle evens from outer PSI. For example, Rust language is injected to Kotlin's string
        // literal. If a user change the literal, we can only be notified that the literal is changed.
        // So we have to invalidate the cached value on any PSI change
        containingFile.virtualFile is VirtualFileWindow -> {
            CachedValueProvider.Result.create(value, PsiModificationTracker.MODIFICATION_COUNT)
        }

        // Invalidate cached value of code fragment on any PSI change
        this is RsCodeFragment -> CachedValueProvider.Result.create(value, PsiModificationTracker.MODIFICATION_COUNT)

        // CachedValueProvider.Result can accept a ModificationTracker as a dependency, so the
        // cached value will be invalidated if the modification counter is incremented.
        else -> {
            val modificationTracker = contextOrSelf<RsModificationTrackerOwner>()?.modificationTracker
            CachedValueProvider.Result.create(value, listOfNotNull(structureModificationTracker, modificationTracker))
        }
    }
}
