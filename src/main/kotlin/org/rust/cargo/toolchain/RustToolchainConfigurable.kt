package org.rust.cargo.toolchain

import com.intellij.openapi.components.service
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.TextComponentAccessor
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.io.FileUtil
import java.io.File
import javax.swing.JComponent
import javax.swing.JPanel

class RustToolchainConfigurable(
    private val project: Project
) : Configurable {

    private lateinit var component: JPanel
    private lateinit var toolchainLocation: TextFieldWithBrowseButton

    init {
        toolchainLocation.addBrowseFolderListener(
            "",
            "Cargo location",
            project,
            FileChooserDescriptorFactory.createSingleFileOrFolderDescriptor(),
            TextComponentAccessor.TEXT_FIELD_WHOLE_TEXT,
            false
        )
    }

    override fun reset() {
        toolchainLocation.text = project.toolchain?.homeDirectory
            ?: suggestToolchainLocation()?.absolutePath
    }

    override fun apply() {
        val projectSettings = project.service<RustProjectSettingsService>()
        projectSettings.toolchain = RustToolchain(toolchainLocation.text)
    }

    override fun isModified(): Boolean {
        return toolchainLocation.text != project.toolchain?.homeDirectory
    }

    override fun disposeUIResources() {
    }

    override fun createComponent(): JComponent? = component

    override fun getDisplayName(): String? = "Cargo"

    override fun getHelpTopic(): String? = null
}

private fun suggestToolchainLocation(): File? = Suggestions.all().find {
    it.looksLikeToolchainLocation
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

private val File.looksLikeToolchainLocation: Boolean get() = File(this, "cargo").canExecute()

