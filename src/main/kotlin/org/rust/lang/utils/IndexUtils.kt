/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.utils

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.stubs.StubIndex
import com.intellij.psi.stubs.StubIndexKey
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiModificationTracker
import com.intellij.util.containers.ContainerUtil

inline fun <Key, reified Psi : PsiElement> getElements(indexKey: StubIndexKey<Key, Psi>,
                                                       key: Key, project: Project,
                                                       scope: GlobalSearchScope?): Collection<Psi> =
    StubIndex.getElements(indexKey, key, project, scope, Psi::class.java)

inline fun <T, R> findWithCache(project: Project, key: T, find: () -> R): R {
    val cache = CachedValuesManager.getManager(project)
        .getCachedValue(project, {
            CachedValueProvider.Result.create(
                ContainerUtil.newConcurrentMap<T, R>(),
                PsiModificationTracker.MODIFICATION_COUNT
            )
        })

    return cache.getOrPut(key) { find() }
}
