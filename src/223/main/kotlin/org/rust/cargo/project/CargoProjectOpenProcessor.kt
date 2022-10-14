/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.project

import org.rust.cargo.icons.CargoIcons
import javax.swing.Icon

class CargoProjectOpenProcessor : CargoProjectOpenProcessorBase() {
    override val icon: Icon get() = CargoIcons.ICON
    override val name: String get() = "Cargo"
}
