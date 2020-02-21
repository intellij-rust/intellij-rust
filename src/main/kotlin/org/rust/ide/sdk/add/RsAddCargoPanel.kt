package org.rust.ide.sdk.add

import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.util.ui.FormBuilder
import org.rust.cargo.icons.CargoIcons
import org.rust.ide.sdk.RsDetectedSdk
import org.rust.ide.sdk.RsSdkUtils.detectCargoSdks
import org.rust.ide.sdk.setup
import java.awt.BorderLayout
import javax.swing.Icon

class RsAddCargoPanel(
//    private val module: Module?,
//    private val existingSdks: List<Sdk>,
//    private val context: UserDataHolderBase
) : RsAddSdkPanel() {
    override val panelName: String = "Cargo"
    override val icon: Icon = CargoIcons.ICON

    private val sdkComboBox: RsSdkPathChoosingComboBox = RsSdkPathChoosingComboBox()

    init {
        layout = BorderLayout()

        val formPanel = FormBuilder.createFormBuilder()
            .addLabeledComponent("Cargo executable:", sdkComboBox)
            .panel
        add(formPanel, BorderLayout.NORTH)
        addToolchainsAsync(sdkComboBox) {
            detectCargoSdks(module, existingSdks, context)
                .takeIf { it.isNotEmpty() || filterCargoSdks(existingSdks).isNotEmpty() }
                ?: getSdksToInstall()
        }
    }

    override fun validateAll(): List<ValidationInfo> = listOfNotNull(validateSdkComboBox(sdkComboBox, this))

    override fun getOrCreateSdk(): Sdk? =
        when (val sdk = sdkComboBox.selectedSdk) {
            is RsDetectedSdk -> sdk.setup(existingSdks)
//            is RsSdkToInstall -> sdk
//                .install(module) { detectCargoSdks(module, existingSdks, context) }
//                .setup(existingSdks)
            else -> sdk
        }
}
