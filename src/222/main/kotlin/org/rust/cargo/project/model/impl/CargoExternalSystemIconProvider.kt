/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.project.model.impl

import com.intellij.openapi.externalSystem.ui.ExternalSystemIconProvider
import org.rust.cargo.icons.CargoIcons
import javax.swing.Icon

class CargoExternalSystemIconProvider : ExternalSystemIconProvider {
    override val reloadIcon: Icon get() = CargoIcons.RELOAD_ICON
}
