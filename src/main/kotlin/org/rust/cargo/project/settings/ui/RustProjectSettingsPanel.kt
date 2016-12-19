package org.rust.cargo.project.settings.ui

import backcompat.ui.layout.CCFlags
import backcompat.ui.layout.LayoutBuilder
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.options.ConfigurationException
import com.intellij.openapi.ui.TextComponentAccessor
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.openapi.util.Disposer
import com.intellij.ui.DocumentAdapter
import com.intellij.ui.JBColor
import com.intellij.util.Alarm
import org.rust.cargo.project.settings.RustProjectSettingsService
import org.rust.cargo.toolchain.RustToolchain
import javax.swing.JCheckBox
import javax.swing.JLabel
import javax.swing.JTextField
import javax.swing.event.DocumentEvent

class RustProjectSettingsPanel {
    data class Data(
        val toolchain: RustToolchain?,
        val autoUpdateEnabled: Boolean
    ) {
        fun applyTo(settings: RustProjectSettingsService) {
            settings.autoUpdateEnabled = autoUpdateEnabled
            settings.toolchain = toolchain
        }
    }

    private val disposable: Disposable = Disposer.newDisposable()
    fun disposeUIResources() = Disposer.dispose(disposable)

    private val versionUpdateAlarm = Alarm(Alarm.ThreadToUse.POOLED_THREAD, disposable)

    private val toolchainLocationField = TextFieldWithBrowseButton(null, disposable)
    private val autoUpdateEnabled = JCheckBox()
    private val rustVersion = JLabel()
    private val cargoVersion = JLabel()
    private val rustupVersion = JLabel()

    private val versionUpdateDelayMillis = 200

    var data: Data
        get() = Data(
            RustToolchain(toolchainLocationField.text),
            autoUpdateEnabled.isSelected
        )
        set(value) {
            toolchainLocationField.text = value.toolchain?.location
            autoUpdateEnabled.isSelected = value.autoUpdateEnabled
        }

    fun attachTo(layout: LayoutBuilder) = with(layout) {
        toolchainLocationField.addBrowseFolderListener(
            "",
            "Cargo location",
            null,
            FileChooserDescriptorFactory.createSingleFolderDescriptor(),
            TextComponentAccessor.TEXT_FIELD_WHOLE_TEXT,
            false
        )
        listenForUpdates(toolchainLocationField.textField)
        Disposer.register(disposable, toolchainLocationField)

        data = Data(
            RustToolchain.suggest(),
            autoUpdateEnabled = true
        )

        row("Toolchain location") { toolchainLocationField(CCFlags.pushX) }
        row("Rustc") { rustVersion() }
        row("Cargo") { cargoVersion() }
        row("Rustup") { rustupVersion() }
    }

    @Throws(ConfigurationException::class)
    fun validateSettings() {
        val toolchain = data.toolchain ?: return
        if (!toolchain.looksLikeValidToolchain()) {
            throw ConfigurationException("Invalid toolchain location: can't find Cargo in ${toolchain.location}")
        }
    }

    private fun listenForUpdates(textField: JTextField) {
        var previousLocation = textField.text

        textField.document.addDocumentListener(object : DocumentAdapter() {
            override fun textChanged(e: DocumentEvent?) {
                val currentLocation = textField.text
                if (currentLocation != previousLocation) {
                    scheduleVersionUpdate(currentLocation)
                    previousLocation = currentLocation
                }
            }
        })
    }

    private fun scheduleVersionUpdate(toolchainLocation: String) {
        versionUpdateAlarm.cancelAllRequests()
        versionUpdateAlarm.addRequest({
            val versionInfo = RustToolchain(toolchainLocation).queryVersions()
            updateVersion(versionInfo)
        }, versionUpdateDelayMillis)
    }

    private fun updateVersion(info: RustToolchain.VersionInfo) {
        ApplicationManager.getApplication().invokeLater({
            if (Disposer.isDisposed(disposable)) return@invokeLater

            val labelToVersion = listOf(
                rustVersion to info.rustc?.semver,
                cargoVersion to info.cargo?.semver,
                rustupVersion to info.rustup
            )

            for ((label, version) in labelToVersion) {
                if (version == null) {
                    label.text = "N/A"
                    label.foreground = JBColor.RED
                } else {
                    label.text = version.parsedVersion
                    label.foreground = JBColor.foreground()
                }
            }

            if (info.cargo?.hasMetadataCommand == false) {
                cargoVersion.foreground = JBColor.RED
            }
        }, ModalityState.any())
    }
}
