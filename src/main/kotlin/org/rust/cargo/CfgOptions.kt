/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo

import com.intellij.util.SmartList
import org.jetbrains.annotations.TestOnly

class CfgOptions(
    val keyValueOptions: Map<String, List<String>>,
    val nameOptions: Set<String>
) {
    fun isNameEnabled(name: String): Boolean =
        name in nameOptions

    fun isNameValueEnabled(name: String, value: String): Boolean =
        keyValueOptions[name]?.contains(value) ?: false

    companion object {
        fun parse(rawCfgOptions: List<String>): CfgOptions {
            val knownKeyValueOptions = hashMapOf<String, SmartList<String>>()
            val knownNameOptions = hashSetOf<String>()

            for (option in rawCfgOptions) {
                val parts = option.split('=', limit = 2)
                val key = parts.getOrNull(0)
                val value = parts.getOrNull(1)?.trim('"')

                if (key != null && value != null) {
                    knownKeyValueOptions.getOrPut(key, { SmartList() }).add(value)
                } else if (key != null) {
                    knownNameOptions.add(key)
                }
            }

            return CfgOptions(knownKeyValueOptions, knownNameOptions)
        }

        @TestOnly
        val DEFAULT: CfgOptions = CfgOptions(
            keyValueOptions = hashMapOf(
                "target_has_atomic" to listOf("8", "16", "32", "64", "ptr", "cas"),
                "target_arch" to listOf("x86_64"),
                "target_endian" to listOf("little"),
                "target_env" to listOf("gnu"),
                "target_family" to listOf("unix"),
                "target_os" to listOf("linux"),
                "target_pointer_width" to listOf("64"),
                "feature" to listOf("use_std")
            ),

            nameOptions = hashSetOf("debug_assertions", "unix")
        )

        @TestOnly
        val EMPTY: CfgOptions = CfgOptions(emptyMap(), emptySet())

        @TestOnly
        const val TEST: String = "intellij_rust"
    }
}
