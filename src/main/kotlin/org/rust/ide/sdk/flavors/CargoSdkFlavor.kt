/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.sdk.flavors

import java.io.File

object CargoSdkFlavor : RsSdkFlavor {
    override val name: String = "Cargo"

    override fun isValidSdkPath(file: File): Boolean {
        val name = file.nameWithoutExtension.toLowerCase()
        return name.startsWith("cargo") || name.startsWith("xargo")
    }
}
