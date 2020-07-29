/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.runconfig.filters

object FilterUtils {
    /**
     * Normalizes function path:
     * - Removes angle brackets from the element path, including enclosed contents when necessary.
     * - Removes closure markers.
     * Examples:
     * - <core::option::Option<T>>::unwrap -> core::option::Option::unwrap
     * - std::panicking::default_hook::{{closure}} -> std::panicking::default_hook
     */
    fun normalizeFunctionPath(function: String): String {
        var str = function
        while (str.endsWith("::{{closure}}")) {
            str = str.substringBeforeLast("::")
        }
        while (true) {
            val range = str.findAngleBrackets() ?: break
            val idx = str.indexOf("::", range.start + 1)
            str = if (idx < 0 || idx > range.endInclusive) {
                str.removeRange(range)
            } else {
                str.removeRange(IntRange(range.endInclusive, range.endInclusive))
                    .removeRange(IntRange(range.start, range.start))
            }
        }
        return str
    }

    /**
     * Finds the range of the first matching angle brackets within the string.
     */
    private fun String.findAngleBrackets(): IntRange? {
        var start = -1
        var counter = 0
        loop@ for ((index, char) in this.withIndex()) {
            when (char) {
                '<' -> {
                    if (start < 0) {
                        start = index
                    }
                    counter += 1
                }
                '>' -> counter -= 1
                else -> continue@loop
            }
            if (counter == 0) {
                return IntRange(start, index)
            }
        }
        return null
    }
}
