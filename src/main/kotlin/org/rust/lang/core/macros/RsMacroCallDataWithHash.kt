/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.macros

import org.rust.stdext.HashCode

class RsMacroCallDataWithHash(
    val data: RsMacroCallData,
    val bodyHash: HashCode?
) {
    fun hashWithEnv(): HashCode? {
        val bodyHash = bodyHash ?: return null
        val env = data.packageEnv
            .entries
            .sortedBy { it.key }
            .joinToString(separator = ";") { it.key + "=" + it.value }
        val envHash = HashCode.compute(env)
        return HashCode.mix(bodyHash, envHash)
    }
}
