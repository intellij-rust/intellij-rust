/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.sdk

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.projectRoots.AdditionalDataConfigurable
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.projectRoots.SdkModel
import com.intellij.openapi.util.Disposer
import com.intellij.ui.layout.panel
import com.intellij.util.ui.JBUI
import org.rust.ide.sdk.RsSdkUpdater.Companion.updateSdkVersion
import javax.swing.JComponent

class RsAdditionalDataConfigurable(private val sdkModel: SdkModel) : AdditionalDataConfigurable {
    private lateinit var editableSdk: Sdk

    private val sdkAdditionalDataPanel: RsSdkAdditionalDataPanel = RsSdkAdditionalDataPanel()

    private val sdkListener: SdkModel.Listener = object : SdkModel.Listener {
        override fun sdkHomeSelected(sdk: Sdk, newSdkHome: String) {
            if (sdk.name == editableSdk.name) {
                sdkAdditionalDataPanel.notifySdkHomeChanged(newSdkHome)
            }
        }
    }

    init {
        sdkModel.addListener(sdkListener)
    }

    override fun setSdk(sdk: Sdk) {
        editableSdk = sdk
        sdkAdditionalDataPanel.data = sdk.sdkAdditionalData as? RsSdkAdditionalData
        sdkAdditionalDataPanel.notifySdkHomeChanged(sdk.homePath)
    }

    override fun createComponent(): JComponent = panel {
        sdkAdditionalDataPanel.attachTo(this, JBUI.scale(24))
    }

    override fun isModified(): Boolean {
        val sdkAdditionalData = editableSdk.sdkAdditionalData
        val data = sdkAdditionalDataPanel.data
        return sdkAdditionalData != data
    }

    override fun apply() {
        sdkAdditionalDataPanel.validateSettings()
        editableSdk.modify { it.sdkAdditionalData = sdkAdditionalDataPanel.data }
        updateSdkVersion(editableSdk)
        ApplicationManager.getApplication().messageBus
            .syncPublisher(RsSdkAdditionalData.RUST_ADDITIONAL_DATA_TOPIC)
            .sdkAdditionalDataChanged(editableSdk)
    }

    override fun reset() {
        sdkAdditionalDataPanel.data = editableSdk.sdkAdditionalData as? RsSdkAdditionalData
    }

    override fun disposeUIResources() {
        Disposer.dispose(sdkAdditionalDataPanel)
        sdkModel.removeListener(sdkListener)
    }
}
