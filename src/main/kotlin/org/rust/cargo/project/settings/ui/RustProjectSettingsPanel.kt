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
import org.rust.cargo.toolchain.Version
import org.rust.cargo.toolchain.suggestToolchain
import javax.swing.*
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
            suggestToolchain(),
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
            val toolchain = RustToolchain(toolchainLocation)

            val rustc = toolchain.queryRustcVersion()
            val cargo = toolchain.queryCargoVersion()

            updateVersion(rustc, cargo)

        }, versionUpdateDelayMillis)
    }

    private fun updateVersion(rustc: Version?, cargo: Version?) {
        ApplicationManager.getApplication().invokeLater({
            if (!Disposer.isDisposed(disposable)) {
                if (rustc != null) {
                    rustVersion.text        = rustc.release
                    rustVersion.foreground  = JBColor.foreground()
                } else {
                    rustVersion.text        = "N/A"
                    rustVersion.foreground  = JBColor.RED
                }

                if (cargo != null) {
                    cargoVersion.text       = cargo.release
                    cargoVersion.foreground = if (cargo >= RustToolchain.CARGO_LEAST_COMPATIBLE_VERSION) JBColor.foreground() else JBColor.RED
                } else {
                    cargoVersion.text       = "N/A"
                    cargoVersion.foreground = JBColor.RED
                }
            }
        }, ModalityState.any())
    }
}

