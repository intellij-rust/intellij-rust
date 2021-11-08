/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.util

import com.intellij.openapi.components.service
import org.rust.cargo.toolchain.impl.RustcVersion

/**
 * This cache is used *only* in unit tests.
 *
 * We assume that `sysroot`, a `list of targets`, `--print cfg` and `stdlib` are not changed
 * (in unit tests) until the version of rustc is changed, so we can cache these values for all tests.
 * The cache significantly speeds up heavy tests with a full toolchain (`RsWithToolchainTestBase`)
 */
interface UnitTestRustcCacheService {
    fun <T> cachedInner(
        rustcVersion: RustcVersion?,
        cls: Class<T>,
        cacheIf: () -> Boolean,
        computation: () -> T
    ): T

    companion object {
        inline fun <reified T> cached(
            rustcVersion: RustcVersion?,
            noinline cacheIf: () -> Boolean = { true },
            noinline computation: () -> T
        ): T {
            return service<UnitTestRustcCacheService>().cachedInner(rustcVersion, T::class.java, cacheIf, computation)
        }
    }
}

class UnitTestRustcCacheServiceImpl : UnitTestRustcCacheService {
    override fun <T> cachedInner(
        rustcVersion: RustcVersion?,
        cls: Class<T>,
        cacheIf: () -> Boolean,
        computation: () -> T
    ): T {
        return computation()
    }

}
