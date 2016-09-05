package org.rust.cargo.project.settings.ui

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
import javax.swing.JPanel
import javax.swing.JTextField
import javax.swing.event.DocumentEvent

class RustProjectSettingsPanel : JPanel() {
    data class Data(
        val toolchain: RustToolchain?,
        val autoUpdateEnabled: Boolean
    ) {
        fun applyTo(settings: RustProjectSettingsService) {
            settings.autoUpdateEnabled = autoUpdateEnabled
            settings.toolchain = toolchain
        }
    }

    private val disposable = Disposer.newDisposable()
    @Suppress("unused") // required by GUI designer to use this form as an element of other forms
    private lateinit var root: JPanel
    private lateinit var toolchainLocationField: TextFieldWithBrowseButton

    private lateinit var autoUpdateEnabled: JCheckBox
    private lateinit var rustVersion: JLabel
    private lateinit var cargoVersion: JLabel
    private lateinit var rustupVersion: JLabel
    private lateinit var versionUpdateAlarm: Alarm

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

    init {
        toolchainLocationField.addBrowseFolderListener(
            "",
            "Cargo location",
            null,
            FileChooserDescriptorFactory.createSingleFileOrFolderDescriptor(),
            TextComponentAccessor.TEXT_FIELD_WHOLE_TEXT,
            false
        )
        versionUpdateAlarm = Alarm(Alarm.ThreadToUse.POOLED_THREAD, disposable)
        listenForUpdates(toolchainLocationField.textField)
        Disposer.register(disposable, toolchainLocationField)


        data = Data(
            RustToolchain.suggest(),
            autoUpdateEnabled = true
        )
    }

    fun disposeUIResources() {
        Disposer.dispose(disposable)
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

