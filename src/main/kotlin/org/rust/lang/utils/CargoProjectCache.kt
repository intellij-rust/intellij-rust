/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.utils

import com.intellij.openapi.util.Key
import com.intellij.psi.util.CachedValue
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.util.containers.ContainerUtil
import org.rust.cargo.project.model.CargoProject
import org.rust.lang.core.psi.rustStructureModificationTracker
import org.rust.stdext.Cache
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap

/**
 * Similar to [org.rust.openapiext.ProjectCache], but based on [CargoProject]
 */
class CargoProjectCache<in T: Any, R: Any>(
    cacheName: String,
    private val dependencyGetter: (CargoProject) -> Any = { it.project.rustStructureModificationTracker }
) {
    init {
        if (!registered.add(cacheName)) {
            error("""
                CargoProjectCache `$cacheName` is already registered.
                Make sure ProjectCache is static, that is, put it inside companion object.
            """.trimIndent())
        }
    }

    private val cacheKey: Key<CachedValue<ConcurrentMap<T, R>>> = Key.create(cacheName)

    fun getOrPut(project: CargoProject, key: T, defaultValue: () -> R): R {
        val cache = getCacheInternal(project)
        return cache.getOrPut(key) { defaultValue() }
    }

    fun getCache(project: CargoProject): Cache<T, R> =
        Cache.fromConcurrentMap(getCacheInternal(project))

    private fun getCacheInternal(project: CargoProject): ConcurrentMap<T, R> {
        return CachedValuesManager.getManager(project.project)
            .getCachedValue(project, cacheKey, {
                CachedValueProvider.Result.create(
                    ConcurrentHashMap(),
                    dependencyGetter(project)
                )
            }, false)
    }

    companion object {
        private val registered = ContainerUtil.newConcurrentSet<String>()
    }
}
