/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.project

import org.rust.cargo.icons.CargoIcons
import javax.swing.Icon

class CargoProjectOpenProcessor : CargoProjectOpenProcessorBase() {
    override fun getIcon(): Icon = CargoIcons.ICON
    override fun getName(): String = "Cargo"
}
