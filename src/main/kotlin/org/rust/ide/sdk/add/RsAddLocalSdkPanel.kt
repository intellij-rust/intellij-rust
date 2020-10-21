/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.sdk.add

import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.openapi.util.Disposer
import com.intellij.ui.layout.panel
import org.rust.ide.icons.RsIcons
import org.rust.ide.sdk.RsDetectedSdk
import org.rust.ide.sdk.RsSdkAdditionalData
import org.rust.ide.sdk.RsSdkAdditionalDataPanel
import org.rust.ide.sdk.RsSdkAdditionalDataPanel.Companion.validateSdkAdditionalDataPanel
import org.rust.ide.sdk.RsSdkPathChoosingComboBox
import org.rust.ide.sdk.RsSdkPathChoosingComboBox.Companion.addToolchainsAsync
import org.rust.ide.sdk.RsSdkPathChoosingComboBox.Companion.validateSdkComboBox
import org.rust.ide.sdk.RsSdkUtils.detectRustSdks
import java.awt.BorderLayout
import java.awt.event.ItemEvent
import javax.swing.Icon

class RsAddLocalSdkPanel(private val existingSdks: List<Sdk>) : RsAddSdkPanel() {
    override val panelName: String = "Local toolchain"
    override val icon: Icon = RsIcons.RUST

    private val sdkPathComboBox: RsSdkPathChoosingComboBox = RsSdkPathChoosingComboBox()
    private val homePath: String? get() = sdkPathComboBox.selectedSdk?.homePath

    private val sdkAdditionalDataPanel: RsSdkAdditionalDataPanel = RsSdkAdditionalDataPanel()
    private val data: RsSdkAdditionalData? get() = sdkAdditionalDataPanel.data

    init {
        layout = BorderLayout()
        val formPanel = panel {
            row("Toolchain path:") { sdkPathComboBox() }
            sdkAdditionalDataPanel.attachTo(this)
        }
        add(formPanel, BorderLayout.NORTH)

        sdkPathComboBox.childComponent.addItemListener { event ->
            if (event.stateChange == ItemEvent.SELECTED) {
                sdkAdditionalDataPanel.notifySdkHomeChanged(homePath)
            }
        }
        addToolchainsAsync(sdkPathComboBox) { detectRustSdks(existingSdks) }
    }

    override fun getOrCreateSdk(): Sdk? =
        when (val sdk = sdkPathComboBox.selectedSdk) {
            is RsDetectedSdk -> data?.let { sdk.setup(existingSdks, it) }
            else -> sdk
        }

    override fun validateAll(): List<ValidationInfo> = listOfNotNull(
        validateSdkComboBox(sdkPathComboBox),
        validateSdkAdditionalDataPanel(sdkAdditionalDataPanel)
    )

    override fun dispose() {
        Disposer.dispose(sdkAdditionalDataPanel)
    }
}
