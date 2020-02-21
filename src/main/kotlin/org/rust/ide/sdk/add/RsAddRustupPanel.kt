package org.rust.ide.sdk.add

import com.intellij.execution.ExecutionException
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.ui.DocumentAdapter
import com.intellij.util.PathUtil
import com.intellij.util.SystemProperties
import com.intellij.util.ui.FormBuilder
import org.jetbrains.annotations.SystemIndependent
import org.rust.ide.icons.RsIcons
import java.awt.BorderLayout
import java.io.File
import javax.swing.Icon
import javax.swing.JComboBox
import javax.swing.event.DocumentEvent

class RsAddRustupPanel(
//    private val project: Project?,
//    private val module: Module?,
//    private val existingSdks: List<Sdk>
) : RsAddSdkPanel() {
    override val panelName: String = "Rustup"
    override val icon: Icon = RsIcons.RUST

    private val rustupPathField = TextFieldWithBrowseButton().apply {
        val path = RsRustupComponentService.getInstance().PREFERRED_RUSTUP_PATH
        path.let {
            text = it
        }
        addBrowseFolderListener(
            "Select Path to Rustup Executable",
            null,
            project,
            FileChooserDescriptorFactory.createSingleFileOrExecutableAppDescriptor()
        )
        textField.document.addDocumentListener(object : DocumentAdapter() {
            override fun textChanged(e: DocumentEvent) {
                updatePathField()
            }
        })
    }
    private val toolchainsField: JComboBox<String>
    private val targetsField: JComboBox<String>

    init {
        layout = BorderLayout()

//        toolchainsField = ComboBox(supportedLanguageLevels.toTypedArray()).apply {
//            selectedItem = if (itemCount > 0) getItemAt(0) else null
//        }

        toolchainsField = ComboBox(supportedLanguageLevels.toTypedArray()).apply {
            selectedItem = if (itemCount > 0) getItemAt(0) else null
        }

        targetsField = ComboBox(supportedLanguageLevels.toTypedArray()).apply {
            selectedItem = if (itemCount > 0) getItemAt(0) else null
        }

        updatePathField()

        val formPanel = FormBuilder.createFormBuilder()
            .addLabeledComponent("Rustup executable:", rustupPathField)
            .addLabeledComponent("Toolchain:", toolchainsField)
            .addLabeledComponent("Target:", targetsField)
            .panel
        add(formPanel, BorderLayout.NORTH)
    }

    override fun validateAll(): List<ValidationInfo> = listOfNotNull(validateRustupPath())

    override fun getOrCreateSdk(): Sdk? {
        val condaPath = rustupPathField.text
        val task = object : Task.WithResult<String, ExecutionException>(project, "Creating Conda Environment", false) {
            override fun compute(indicator: ProgressIndicator): String {
                indicator.isIndeterminate = true
                return PyCondaPackageManagerImpl.createVirtualEnv(condaPath, pathField.text, selectedLanguageLevel)
            }
        }
        val shared = makeSharedField.isSelected
        val associatedPath = if (!shared) projectBasePath else null
        val sdk = createSdkByGenerateTask(task, existingSdks, null, associatedPath, null) ?: return null
        if (!shared) {
            sdk.associateWithModule(module, newProjectPath)
        }
        PyCondaPackageService.getInstance().PREFERRED_CONDA_PATH = condaPath
        return sdk
    }

    override fun addChangeListener(listener: Runnable) {
        val documentListener = object : DocumentAdapter() {
            override fun textChanged(e: DocumentEvent) {
                listener.run()
            }
        }
        pathField.textField.document.addDocumentListener(documentListener)
        rustupPathField.textField.document.addDocumentListener(documentListener)
    }

    private fun updatePathField() {
        val baseDir = defaultBaseDir ?: "${SystemProperties.getUserHome()}/.conda/envs"
        val dirName = PathUtil.getFileName(projectBasePath ?: "untitled")
        pathField.text = FileUtil.toSystemDependentName("$baseDir/$dirName")
    }

    private fun validateRustupPath(): ValidationInfo? {
        val text = rustupPathField.text
        val file = File(text)
        val message = when {
            text.isBlank() -> "Rustup executable path is empty"
            !file.exists() -> "Rustup executable not found"
            !file.isFile || !file.canExecute() -> "Rustup executable path is not an executable file"
            else -> return null
        }
        return ValidationInfo(message)
    }

    private val defaultBaseDir: String?
        get() {
            val conda = rustupPathField.text
            val condaFile = LocalFileSystem.getInstance().findFileByPath(conda) ?: return null
            return condaFile.parent?.parent?.findChild("envs")?.path
        }

    private val projectBasePath: @SystemIndependent String?
        get() = newProjectPath ?: module?.basePath ?: project?.basePath

    private val selectedLanguageLevel: String
        get() = toolchainsField.getItemAt(toolchainsField.selectedIndex)
}
