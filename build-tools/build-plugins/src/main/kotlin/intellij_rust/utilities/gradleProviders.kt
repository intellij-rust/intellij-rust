/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package intellij_rust.utilities

import org.gradle.api.provider.Provider

/** Invert the value of this provider */
operator fun Provider<Boolean>.not(): Provider<Boolean> =
    map { !it }

fun Provider<String>.toInt(): Provider<Int> = map { it.toInt() }

fun Provider<String>.toBoolean(): Provider<Boolean> = map { it.toBoolean() }.orElse(false)
