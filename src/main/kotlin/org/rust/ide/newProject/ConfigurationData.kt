/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.newProject

import org.rust.cargo.project.settings.ui.RustProjectSettingsPanel

data class ConfigurationData(
    val settings: RustProjectSettingsPanel.Data,
    val createBinary: Boolean
)
