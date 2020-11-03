/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust

import com.intellij.openapiext.TestmarkPred
import org.rust.lang.core.resolve2.isNewResolveEnabled

@Target(AnnotationTarget.FUNCTION, AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class UseNewResolve

@Target(AnnotationTarget.FUNCTION, AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class IgnoreInNewResolve

fun TestmarkPred.ignoreInNewResolve(): TestmarkPred {
    if (!isNewResolveEnabled) return this
    return object : TestmarkPred {
        override fun <T> checkHit(f: () -> T): T = f()

        override fun <T> checkNotHit(f: () -> T): T = f()
    }
}
