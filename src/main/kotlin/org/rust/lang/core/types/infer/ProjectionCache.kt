/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.types.infer

import org.rust.lang.core.types.ty.Ty
import org.rust.lang.core.types.ty.TyProjection

/**
 * The projection cache. Unlike the standard caches, this can
 * include infcx-dependent type variables - therefore, we have to roll
 * the cache back each time we roll a snapshot back, to avoid assumptions
 * on yet-unresolved inference variables.
 *
 * Because of that, projection cache entries can be "stranded" and left
 * inaccessible when type variables inside the key are resolved. We make no
 * attempt to recover or remove "stranded" entries, but rather let them be
 * (for the lifetime of the [RsInferenceContext]).
 */
class ProjectionCache {
    private val map: SnapshotMap<TyProjection, ProjectionCacheEntry> = SnapshotMap()

    fun startSnapshot(): Snapshot = map.startSnapshot()

    /**
     * Try to start normalize `key`. If we hits the cache (normalization
     * already occurred), the function just returns the nonnull value from
     * the cache. Otherwise it puts [ProjectionCacheEntry.InProgress] value
     * to the cache and returns `null`
     */
    fun tryStart(key: TyProjection): ProjectionCacheEntry? {
        return map.get(key) ?: run {
            map.put(key, ProjectionCacheEntry.InProgress)
            null
        }
    }

    private fun put(key: TyProjection, value: ProjectionCacheEntry) {
        map.put(key, value) ?: error("never started projecting for `$key`")
    }

    fun putTy(key: TyProjection, value: TyWithObligations<Ty>) {
        put(key, ProjectionCacheEntry.NormalizedTy(value))
    }

    fun ambiguous(key: TyProjection) {
        put(key, ProjectionCacheEntry.Ambiguous)
    }

    fun error(key: TyProjection) {
        put(key, ProjectionCacheEntry.Error)
    }
}

sealed class ProjectionCacheEntry {
    object InProgress: ProjectionCacheEntry()
    object Ambiguous: ProjectionCacheEntry()
    object Error: ProjectionCacheEntry()
    class NormalizedTy(val ty: TyWithObligations<Ty>): ProjectionCacheEntry()
}
