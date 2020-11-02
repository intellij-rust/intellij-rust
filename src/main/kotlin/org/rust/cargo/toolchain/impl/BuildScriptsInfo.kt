/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.toolchain.impl

import org.rust.cargo.project.workspace.PackageId

class BuildScriptsInfo(private val messages: Map<PackageId, BuildScriptMessage>) {
    val containsOutDirInfo: Boolean
        get() = messages.entries.firstOrNull()?.value?.out_dir != null

    operator fun get(packageId: PackageId): BuildScriptMessage? = messages[packageId]

    fun replacePaths(replacer: (String) -> String): BuildScriptsInfo =
        BuildScriptsInfo(
            messages.mapValues { (_, message) ->
                if (message.out_dir == null) return@mapValues message
                message.copy(out_dir = replacer(message.out_dir))
            }
        )
}
