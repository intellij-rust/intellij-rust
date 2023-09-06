/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package intellij_rust.utilities

import org.gradle.api.provider.Provider

/** Invert the [Boolean] value of this provider. */
operator fun Provider<Boolean>.not(): Provider<Boolean> =
    map { !it }

/** Convert the contained [String] value to an [Int], failing if the value is not convertable. */
fun Provider<String>.toInt(): Provider<Int> = map {
    it.toIntOrNull() ?: error("Tried to convert Provider<String> to an Int, but '$it' is not an Int")
}

/** Convert the contained [String] value to a [Boolean], using [default] if the provider has no value. */
fun Provider<String>.toBoolean(default: Boolean = false): Provider<Boolean> =
    map { it.toBoolean() }.orElse(default)
