/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.toolchain.impl

import org.rust.cargo.project.workspace.PackageId
import org.rust.cargo.toolchain.BuildScriptMessage

class BuildScriptsInfo(private val messages: Map<PackageId, BuildScriptMessage>) {
    operator fun get(packageId: PackageId): BuildScriptMessage? = messages[packageId]
}
