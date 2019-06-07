/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.clion.cargo

import com.jetbrains.cidr.execution.CidrBuildConfiguration
import org.rust.cargo.runconfig.CargoBuildableElement

class CargoBuildConfiguration : CargoBuildableElement, CidrBuildConfiguration {
    override fun getName(): String = "Cargo"
}
