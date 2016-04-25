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
import org.rust.cargo.toolchain.suggestToolchain
import javax.swing.*
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

    private val disposable = Disposer.newDisposable("CargoSettingsPanel")
    private lateinit var component: JPanel
    private lateinit var toolchainLocationField: TextFieldWithBrowseButton

    private lateinit var autoUpdateEnabled: JCheckBox
    private lateinit var rustVersion: JLabel
    private lateinit var versionUpdateAlarm: Alarm

    private val versionUpdateDelayMillis = 200

    fun createComponent(): JComponent {
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

        return component
    }

    fun disposeUIResources() {
        Disposer.dispose(disposable)
    }

    var data: Data
        get() = Data(
            RustToolchain(toolchainLocationField.text),
            autoUpdateEnabled.isSelected
        )
        set(value) {
            toolchainLocationField.text = value.toolchain?.location
            autoUpdateEnabled.isSelected = value.autoUpdateEnabled
        }

    @Throws(ConfigurationException::class)
    fun validate() {
        val toolchain = data.toolchain ?: return
        if (!toolchain.looksLikeValidToolchain()) {
            throw ConfigurationException("Invalid toolchain location: can't find cargo in ${toolchain.location}")
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
            val version = RustToolchain(toolchainLocation).queryRustcVersion()
            updateVersion(version?.release)
        }, versionUpdateDelayMillis)
    }

    private fun updateVersion(newVersion: String?) {
        ApplicationManager.getApplication().invokeLater({
            if (!Disposer.isDisposed(disposable)) {
                val isInvalid = newVersion == null
                rustVersion.text = if (isInvalid) "N/A" else newVersion
                rustVersion.foreground = if (isInvalid) JBColor.RED else JBColor.foreground()
            }
        }, ModalityState.any())
    }
}

