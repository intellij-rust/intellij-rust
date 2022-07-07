/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.project.configurable

import com.intellij.ide.DataManager
import com.intellij.openapi.options.BoundConfigurable
import com.intellij.openapi.options.ex.Settings
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.gridLayout.HorizontalAlign
import org.rust.RsBundle
import java.awt.Component

// BACKCOMPAT: 2022.2. Just drop it
class CargoPlaceholderConfigurable : BoundConfigurable(RsBundle.message("settings.rust.cargo.name")) {

    override fun createPanel(): DialogPanel {
        var callback = { }

        val panel = panel {
            row {
                link(RsBundle.message("settings.rust.cargo.moved.label")) { callback() }
                    .resizableColumn()
                    .horizontalAlign(HorizontalAlign.CENTER)
            }.resizableRow()
        }

        callback = { openCargoSettings(panel) }

        return panel
    }

    private fun openCargoSettings(component: Component) {
        val dataContext = DataManager.getInstance().getDataContext(component)
        val settings = Settings.KEY.getData(dataContext)
        if (settings != null) {
            val configurable = settings.find(CargoConfigurable::class.java)
            settings.select(configurable)
        }
    }
}
