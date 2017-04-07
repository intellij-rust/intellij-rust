package org.rust.cargo.project.settings.ui

import com.intellij.openapi.Disposable
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.options.ConfigurationException
import com.intellij.openapi.ui.TextComponentAccessor
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.ui.DocumentAdapter
import com.intellij.ui.JBColor
import com.intellij.ui.layout.CCFlags
import com.intellij.ui.layout.LayoutBuilder
import com.intellij.util.text.SemVer
import org.rust.cargo.project.settings.RustProjectSettingsService
import org.rust.cargo.toolchain.RustToolchain
import org.rust.utils.UiDebouncer
import javax.swing.JCheckBox
import javax.swing.JLabel
import javax.swing.JTextField
import javax.swing.event.DocumentEvent

class RustProjectSettingsPanel : Disposable {
    data class Data(
        val toolchain: RustToolchain?,
        val autoUpdateEnabled: Boolean
    ) {
        fun applyTo(settings: RustProjectSettingsService) {
            settings.data = RustProjectSettingsService.Data(
                toolchain,
                autoUpdateEnabled,
                null
            )
        }
    }

    override fun dispose() {}

    private val versionUpdateDebouncer = UiDebouncer(this)

    private val pathToToolchainField = TextFieldWithBrowseButton(null, this)
    private val autoUpdateEnabled = JCheckBox()
    private val toolchainVersion = JLabel()

    var data: Data
        get() = Data(
            RustToolchain(pathToToolchainField.text),
            autoUpdateEnabled.isSelected
        )
        set(value) {
            // https://youtrack.jetbrains.com/issue/KT-16367
            pathToToolchainField.setText(value.toolchain?.location)
            autoUpdateEnabled.isSelected = value.autoUpdateEnabled
        }

    fun attachTo(layout: LayoutBuilder) = with(layout) {
        pathToToolchainField.addBrowseFolderListener(
            "Select directory with cargo binary",
            null,
            null,
            FileChooserDescriptorFactory.createSingleFolderDescriptor(),
            TextComponentAccessor.TEXT_FIELD_WHOLE_TEXT
        )
        listenForUpdates(pathToToolchainField.textField)

        data = Data(
            RustToolchain.suggest(),
            autoUpdateEnabled = true
        )

        row("Toolchain location:") { pathToToolchainField(CCFlags.pushX) }
        row("Toolchain version:") { toolchainVersion() }
    }

    @Throws(ConfigurationException::class)
    fun validateSettings() {
        val toolchain = data.toolchain ?: return
        if (!toolchain.looksLikeValidToolchain()) {
            throw ConfigurationException("Invalid toolchain location: can't find Cargo in ${toolchain.location}")
        }
    }

    private fun listenForUpdates(textField: JTextField) {
        textField.document.addDocumentListener(object : DocumentAdapter() {
            override fun textChanged(e: DocumentEvent?) {
                val currentLocation = textField.text
                versionUpdateDebouncer.run(
                    onPooledThread = {
                        RustToolchain(currentLocation).queryVersions().rustc.semver
                    },
                    onUiThread = { rustcVersion ->
                        if (rustcVersion == SemVer.UNKNOWN) {
                            toolchainVersion.text = "N/A"
                            toolchainVersion.foreground = JBColor.RED
                        } else {
                            toolchainVersion.text = rustcVersion.parsedVersion
                            toolchainVersion.foreground = JBColor.foreground()
                        }
                    }
                )
            }
        })
    }
}
