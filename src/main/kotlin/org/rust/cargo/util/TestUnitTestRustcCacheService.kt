/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.util

import com.intellij.util.containers.orNull
import org.jetbrains.annotations.TestOnly
import org.rust.cargo.toolchain.impl.RustcVersion
import java.util.*
import java.util.concurrent.ConcurrentHashMap

@TestOnly
class TestUnitTestRustcCacheService : UnitTestRustcCacheService {
    private val cache: ConcurrentHashMap<Pair<RustcVersion, Class<*>>, Optional<Any>> = ConcurrentHashMap()

    @Suppress("UNCHECKED_CAST")
    override fun <T> cachedInner(
        rustcVersion: RustcVersion?,
        cls: Class<T>,
        cacheIf: () -> Boolean,
        computation: () -> T
    ): T {
        if (rustcVersion == null || !cacheIf()) return computation()
        return cache.getOrPut(rustcVersion to cls) { Optional.ofNullable(computation()) }.orNull() as T
    }
}
