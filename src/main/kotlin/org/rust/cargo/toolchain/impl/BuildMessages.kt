/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.toolchain.impl

import org.rust.cargo.project.workspace.PackageId

class BuildMessages(
    private val messages: Map<PackageId, List<CompilerMessage>>,
    val isSuccessful: Boolean
) {

    fun get(packageId: PackageId): List<CompilerMessage> = messages[packageId].orEmpty()

    fun replacePaths(replacer: (String) -> String): BuildMessages =
        BuildMessages(
            messages.mapValues { (_, messages) ->
                messages.map { message ->
                    val outDir = (message as? BuildScriptMessage)?.out_dir ?: return@map message
                    message.copy(out_dir = replacer(outDir))
                }
            },
            isSuccessful
        )

    companion object {
        val DEFAULT = BuildMessages(emptyMap(), true)
        val FAILED = BuildMessages(emptyMap(), false)
    }
}
