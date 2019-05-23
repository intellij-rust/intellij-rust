/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.resolve.ref

import com.intellij.openapi.util.RecursionGuard
import com.intellij.openapi.util.RecursionManager
import com.intellij.psi.PsiElement

class RecursionGuardWrapper private constructor(private val guard: RecursionGuard<PsiElement>) {

    // BACKCOMPAT 2018.3: use `RecursionManager#markStack()`
    @Suppress("DEPRECATION")
    fun markStack(): RecursionGuard.StackStamp = guard.markStack()
    fun <T> doPreventingRecursion(key: PsiElement, memoize: Boolean, computation: () -> T): T? {
        return guard.doPreventingRecursion(key, memoize, computation)
    }

    companion object {
        fun createGuard(id: String): RecursionGuardWrapper {
            val guard = RecursionManager.createGuard<PsiElement>(id)
            return RecursionGuardWrapper(guard)
        }
    }
}
