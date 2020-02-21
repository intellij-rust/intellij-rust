/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.sdk.flavors

import java.io.File

object RustupSdkFlavor : RsSdkFlavor {
    override val name: String = "Rustup"

    override fun isValidSdkPath(file: File): Boolean =
        file.nameWithoutExtension.toLowerCase().startsWith("rustup")
}
