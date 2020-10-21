/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo

import org.jetbrains.annotations.TestOnly

data class CfgOptions(
    val keyValueOptions: Map<String, Set<String>>,
    val nameOptions: Set<String>
) {
    fun isNameEnabled(name: String): Boolean =
        name in nameOptions

    fun isNameValueEnabled(name: String, value: String): Boolean =
        keyValueOptions[name]?.contains(value) ?: false

    companion object {
        fun parse(rawCfgOptions: List<String>): CfgOptions {
            val knownKeyValueOptions = hashMapOf<String, MutableSet<String>>()
            val knownNameOptions = hashSetOf<String>()

            for (option in rawCfgOptions) {
                val parts = option.split('=', limit = 2)
                val key = parts.getOrNull(0)
                val value = parts.getOrNull(1)?.trim('"')

                if (key != null && value != null) {
                    knownKeyValueOptions.getOrPut(key, ::hashSetOf).add(value)
                } else if (key != null) {
                    knownNameOptions.add(key)
                }
            }

            return CfgOptions(knownKeyValueOptions, knownNameOptions)
        }

        val EMPTY: CfgOptions = CfgOptions(emptyMap(), emptySet())

        @TestOnly
        val DEFAULT: CfgOptions = CfgOptions(
            keyValueOptions = hashMapOf(
                "target_has_atomic" to hashSetOf("8", "16", "32", "64", "ptr", "cas"),
                "target_arch" to hashSetOf("x86_64"),
                "target_endian" to hashSetOf("little"),
                "target_env" to hashSetOf("gnu"),
                "target_family" to hashSetOf("unix"),
                "target_os" to hashSetOf("linux"),
                "target_pointer_width" to hashSetOf("64"),
                "feature" to hashSetOf("use_std")
            ),

            nameOptions = hashSetOf("debug_assertions", "unix")
        )

        @TestOnly
        const val TEST: String = "intellij_rust"
    }
}
