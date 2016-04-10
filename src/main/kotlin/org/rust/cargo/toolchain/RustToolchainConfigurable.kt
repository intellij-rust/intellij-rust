package org.rust.cargo.toolchain

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.components.service
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.TextComponentAccessor
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.io.FileUtil
import com.intellij.ui.DocumentAdapter
import com.intellij.ui.JBColor
import com.intellij.util.Alarm
import org.rust.cargo.util.getModules
import org.rust.cargo.util.getService
import java.io.File
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JTextField
import javax.swing.event.DocumentEvent

class RustToolchainConfigurable(
    private val project: Project
) : Configurable {

    private val disposable = Disposer.newDisposable("RustToolchainConfigurableDisposable")

    private lateinit var component: JPanel
    private lateinit var toolchainLocationField: TextFieldWithBrowseButton

    private lateinit var rustVersion: JLabel
    private lateinit var versionUpdateAlarm: Alarm
    private val versionUpdateDelayMillis = 200


    override fun createComponent(): JComponent {
        toolchainLocationField.addBrowseFolderListener(
            "",
            "Cargo location",
            project,
            FileChooserDescriptorFactory.createSingleFileOrFolderDescriptor(),
            TextComponentAccessor.TEXT_FIELD_WHOLE_TEXT,
            false
        )
        versionUpdateAlarm = Alarm(Alarm.ThreadToUse.POOLED_THREAD, disposable)
        listenForUpdates(toolchainLocationField.textField)
        Disposer.register(disposable, toolchainLocationField)
        return component
    }

    override fun reset() {
        toolchainLocationField.text = project.toolchain?.location
            ?: suggestToolchainLocation()?.absolutePath
    }

    override fun apply() {
        val projectSettings = project.service<RustProjectSettingsService>()
        val toolchain = RustToolchain(toolchainLocationField.text)
        projectSettings.toolchain = toolchain
        for (module in project.getModules()) {
            module.getService<CargoMetadataService>().scheduleUpdate(toolchain)
        }
    }

    override fun isModified(): Boolean {
        return toolchainLocationField.text != project.toolchain?.location
    }

    override fun disposeUIResources() {
        Disposer.dispose(disposable)
    }


    override fun getDisplayName(): String? = "Cargo"

    override fun getHelpTopic(): String? = null

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

fun suggestToolchainLocation(): File? = Suggestions.all().find {
    RustToolchain(it.absolutePath).looksLikeValidToolchain()
}

private object Suggestions {
    fun all() = sequenceOf(
        fromRustup(),
        fromPath(),
        forMac(),
        forUnix(),
        forWindows()
    ).flatten()

    private fun fromRustup(): Sequence<File> {
        val file = File(FileUtil.expandUserHome("~/.cargo/bin"))
        return if (file.isDirectory) {
            sequenceOf(file)
        } else {
            emptySequence()
        }
    }

    private fun fromPath(): Sequence<File> = System.getenv("PATH").orEmpty()
        .split(File.pathSeparator)
        .asSequence()
        .filter { !it.isEmpty() }
        .map { File(it) }
        .filter { it.isDirectory }

    private fun forUnix(): Sequence<File> {
        if (!SystemInfo.isUnix) return emptySequence()

        return sequenceOf(File("/usr/local/bin"))
    }

    private fun forMac(): Sequence<File> {
        if (!SystemInfo.isMac) return emptySequence()

        return sequenceOf(File("/usr/local/Cellar/rust/bin"))
    }

    private fun forWindows(): Sequence<File> {
        if (!SystemInfo.isWindows) return emptySequence()

        val programFiles = File(System.getenv("ProgramFiles") ?: return emptySequence())
        if (!programFiles.exists() || !programFiles.isDirectory) return emptySequence()

        return programFiles.listFiles { file -> file.isDirectory }.asSequence()
            .filter { it.nameWithoutExtension.toLowerCase().startsWith("rust") }
            .map { File(it, "bin") }
    }
}

