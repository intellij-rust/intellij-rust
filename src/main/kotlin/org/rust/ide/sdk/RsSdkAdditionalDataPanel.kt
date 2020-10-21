/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.sdk

import com.intellij.openapi.Disposable
import com.intellij.openapi.options.ConfigurationException
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.ui.JBColor
import com.intellij.ui.SimpleListCellRenderer
import com.intellij.ui.components.Link
import com.intellij.ui.layout.LayoutBuilder
import com.intellij.util.io.DigestUtil
import com.intellij.util.text.SemVer
import org.rust.cargo.toolchain.RsToolchain
import org.rust.cargo.toolchain.tools.Rustup
import org.rust.cargo.toolchain.tools.isRustupAvailable
import org.rust.cargo.toolchain.tools.rustc
import org.rust.cargo.toolchain.tools.rustup
import org.rust.ide.sdk.flavors.RustupSdkFlavor
import org.rust.openapiext.UiDebouncer
import org.rust.openapiext.pathToDirectoryTextField
import org.rust.stdext.toPath
import java.awt.event.ItemEvent
import javax.swing.JComponent
import javax.swing.JLabel

class RsSdkAdditionalDataPanel : Disposable {
    private var sdkHome: String? = null
    private var sdkKey: String? = null

    private val toolchainNameComboBox: ComboBox<String> = ComboBox<String>().apply {
        isEnabled = false
        renderer = SimpleListCellRenderer.create("<rustup is not available>") { it }
        addItemListener { event ->
            if (event.stateChange == ItemEvent.SELECTED) {
                update(refreshToolchainList = false)
            }
        }
    }

    private val toolchainName: String?
        get() = toolchainNameComboBox.selectedItem as? String

    private val versionLabel: JLabel = JLabel()

    // Usually, we use `rustup` to find stdlib automatically, but if one does not use rustup,
    // it's possible to provide path to stdlib explicitly.
    private val stdlibPathField: TextFieldWithBrowseButton =
        pathToDirectoryTextField(this, "Select directory with standard library source code")

    private var fetchedSysroot: String? = null

    private val downloadStdlibLink: JComponent = Link("Download via rustup", action = {
        val rustup = toolchain?.rustup() ?: return@Link
        object : Task.Backgroundable(null, "Downloading Rust standard library") {
            override fun shouldStartInBackground(): Boolean = false
            override fun onSuccess() = update(refreshToolchainList = false)
            override fun run(indicator: ProgressIndicator) {
                indicator.isIndeterminate = true
                rustup.downloadStdlib()
            }
        }.queue()
    }).apply { isVisible = false }

    var data: RsSdkAdditionalData?
        get() {
            val sdkKey = sdkKey ?: DigestUtil.randomToken()
            val toolchain = toolchain ?: return null
            val isRustupAvailable = toolchain.isRustupAvailable
            val toolchainName = toolchainName?.takeIf { isRustupAvailable }
            val stdlibPath = stdlibPathField.text.takeIf {
                it.isNotBlank() && !isRustupAvailable && it != fetchedSysroot
            }
            return RsSdkAdditionalData(sdkKey, toolchainName, stdlibPath)
        }
        set(value) {
            sdkKey = value?.sdkKey
            toolchainNameComboBox.safeSetSelectedItem(value?.toolchainName)
            stdlibPathField.text = value?.explicitPathToStdlib ?: ""
            update(refreshToolchainList = true)
        }

    private val toolchain: RsToolchain?
        get() {
            val homePath = sdkHome?.toPath() ?: return null
            return RsToolchain(homePath, toolchainName)
        }

    private val updateDebouncer: UiDebouncer = UiDebouncer(this)

    @Suppress("DEPRECATION")
    fun attachTo(layout: LayoutBuilder, gapLeft: Int = 0) = with(layout) {
        row("Rustup toolchain:") { toolchainNameComboBox(pushX, growX).withLeftGap(gapLeft) }
        row("Toolchain version:") { versionLabel().withLeftGap(gapLeft) }
        row("Standard library:") { stdlibPathField().withLeftGap(gapLeft) }
        row { downloadStdlibLink() }
    }

    @Throws(ConfigurationException::class)
    fun validateSettings() {
        val toolchain = toolchain
        when {
            toolchain == null ->
                throw ConfigurationException("Invalid toolchain")
            !toolchain.looksLikeValidToolchain() ->
                throw ConfigurationException("Invalid toolchain location: can't find Cargo in ${toolchain.location}")
        }
    }

    fun notifySdkHomeChanged(sdkHome: String?) {
        if (this.sdkHome == sdkHome) return
        this.sdkHome = sdkHome
        update(refreshToolchainList = true)
    }


    private fun update(refreshToolchainList: Boolean) {

        data class Data(
            val rustcVersion: SemVer?,
            val stdlibPath: String?,
            val toolchains: List<Rustup.Toolchain>?,
            val isRustupAvailable: Boolean
        )

        updateDebouncer.run(
            onPooledThread = {
                val toolchain = toolchain
                val rustup = toolchain?.rustup()
                val rustc = toolchain?.rustc()
                val rustcVersion = rustc?.queryVersion()?.semver
                val stdlibLocation = rustc?.getStdlibFromSysroot()?.presentableUrl
                val toolchains = if (refreshToolchainList) rustup?.listToolchains().orEmpty() else null
                Data(rustcVersion, stdlibLocation, toolchains, rustup != null)
            },
            onUiThread = { (rustcVersion, stdlibLocation, toolchains, isRustupAvailable) ->
                if (rustcVersion == null) {
                    versionLabel.text = "N/A"
                    versionLabel.foreground = JBColor.RED
                } else {
                    versionLabel.text = rustcVersion.parsedVersion
                    versionLabel.foreground = JBColor.foreground()
                }

                stdlibPathField.isEditable = !isRustupAvailable
                stdlibPathField.button.isEnabled = !isRustupAvailable
                if (stdlibLocation != null && (stdlibPathField.text.isBlank() || isRustupAvailable)) {
                    stdlibPathField.text = stdlibLocation
                }
                fetchedSysroot = stdlibLocation

                downloadStdlibLink.isVisible = isRustupAvailable && stdlibLocation == null

                if (toolchains != null) {
                    val oldSelection = toolchainNameComboBox.selectedItem

                    toolchainNameComboBox.removeAllItems()
                    toolchains.forEach { toolchainNameComboBox.addItem(it.name) }

                    toolchainNameComboBox.selectedItem = when {
                        toolchains.any { it.name == oldSelection } -> oldSelection
                        toolchains.any { it.isDefault } -> toolchains.first { it.isDefault }
                        else -> toolchains.firstOrNull()
                    }

                    toolchainNameComboBox.isEnabled = isRustupAvailable
                }
            }
        )
    }

    override fun dispose() {}

    companion object {

        fun validateSdkAdditionalDataPanel(panel: RsSdkAdditionalDataPanel): ValidationInfo? {
            val sdkPath = panel.sdkHome?.toPath() ?: return null
            if (!RustupSdkFlavor.isValidSdkPath(sdkPath)) return null
            if (panel.toolchainName != null) return null
            return ValidationInfo("Rustup toolchain is not selected", panel.toolchainNameComboBox)
        }

        private fun ComboBox<*>.safeSetSelectedItem(item: Any?) {
            val oldEditable = isEditable
            isEditable = true
            selectedItem = item
            isEditable = oldEditable
        }
    }
}
