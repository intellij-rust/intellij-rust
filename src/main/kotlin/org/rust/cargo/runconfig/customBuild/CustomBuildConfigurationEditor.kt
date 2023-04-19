/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.runconfig.customBuild

import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.options.SettingsEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.ui.components.CheckBox
import com.intellij.ui.dsl.builder.RowLayout
import com.intellij.ui.dsl.builder.panel
import org.rust.RsBundle
import org.rust.openapiext.fullWidthCell
import org.rust.stdext.toPath
import javax.swing.JCheckBox
import javax.swing.JComponent

class CustomBuildConfigurationEditor(val project: Project)
    : SettingsEditor<CustomBuildConfiguration>() {

    private val customOutDir = TextFieldWithBrowseButton().apply {
        isEnabled = false
        val fileChooser = FileChooserDescriptorFactory.createSingleFolderDescriptor().apply {
            title = RsBundle.message("run.config.rust.custom.build.custom.out.dir.label")
        }
        addBrowseFolderListener(null, null, null, fileChooser)
    }

    private val isCustomOutDir: JCheckBox = CheckBox(RsBundle.message("run.config.rust.custom.build.custom.out.dir.label"), false).apply {
        addChangeListener { customOutDir.isEnabled = isSelected }
    }

    override fun createEditor(): JComponent = panel {
        row {
            layout(RowLayout.LABEL_ALIGNED)
            cell(isCustomOutDir)
            fullWidthCell(customOutDir)
        }
    }

    override fun resetEditorFrom(configuration: CustomBuildConfiguration) {
        isCustomOutDir.isSelected = configuration.isCustomOutDir
        customOutDir.text = configuration.customOutDir.toString()
    }

    override fun applyEditorTo(configuration: CustomBuildConfiguration) {
        configuration.isCustomOutDir = isCustomOutDir.isSelected
        configuration.customOutDir = customOutDir.text.toPath()
    }
}
