/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.wsl.sdk

import com.intellij.execution.ExecutionException
import com.intellij.execution.wsl.WSLDistribution
import com.intellij.execution.wsl.WSLDistributionWithRoot
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.ui.messages.showProcessExecutionErrorDialog
import com.jetbrains.plugins.webDeployment.config.WebServerConfig
import org.rust.ide.sdk.RsEditRemoteToolchainDialog
import org.rust.ide.sdk.RsRemoteSdkEditor
import org.rust.ide.sdk.remote.RsRemoteSdkAdditionalDataBase
import org.rust.remote.RsEditRemoteToolchainDialog
import org.rust.remote.RsRemoteSdkEditor
import org.rust.stdext.Result
import org.rust.stdext.RsExecutionException
import org.rust.wsl.RsWslPathBrowser
import org.rust.wsl.getDistribution
import java.awt.FlowLayout
import javax.swing.JComponent
import javax.swing.JPanel

class RsWslRemoteInterpreterDialog(
    private val project: Project,
    existingSdks: MutableCollection<Sdk>
) : RsEditRemoteToolchainDialog, DialogWrapper(project, true) {
    private val panel = RsAddWslPanel(existingSdks.toList())

    init {
        init()
        title = panel.panelName
    }

    override fun setEditing(data: RsRemoteSdkAdditionalData) {
        panel.configure(data)
    }

    override fun setSdkName(name: String) {}

    override fun createCenterPanel() = panel.component as JComponent

    override fun showAndGet(): Boolean {
        val result = super.showAndGet()
        try {
            // Creates SDK and fills it with data from form
            // This sdk will be used as source to copy data to existing one
            panel.complete()
        } catch (e: ExecutionException) {
            val exception = (e as? RsExecutionException ?: e.cause as? RsExecutionException) ?: throw e
            showProcessExecutionErrorDialog(
                project,
                exception.localizedMessage.orEmpty(),
                exception.command,
                exception.stdout,
                exception.stderr,
                exception.exitCode
            )
            return false
        }
        return result
    }

    override fun getSdk(): Sdk = panel.sdk!!
}

private class RsWslChooserDialog(project: Project, distribution: WSLDistribution) : DialogWrapper(project) {
    val text: TextFieldWithBrowseButton = TextFieldWithBrowseButton {
        browser.browsePath(WSLDistributionWithRoot(distribution), this.contentPanel)
    }
    val browser: RsWslPathBrowser = RsWslPathBrowser(text)

    init {
        init()
        title = "Add Path"
    }


    override fun createCenterPanel() =
        JPanel(FlowLayout()).apply {
            add(text)
        }
}

private object RsWslFileChooser : RsRemoteFilesChooser {
    override fun chooseRemoteFiles(project: Project, data: RsRemoteSdkAdditionalDataBase, foldersOnly: Boolean): Array<String> {
        val distribution = data.getDistribution().let {
            when (it) {
                is Result.Success -> it.result
                is Result.Failure -> throw ExecutionException(it.error)
            }
        }

        val dialog = RsWslChooserDialog(project, distribution)
        if (dialog.showAndGet()) {
            return arrayOf(WebServerConfig.RemotePath(dialog.text.text).path)
        }

        return emptyArray()
    }
}

class RsWslSdkEditor : RsRemoteSdkEditor {

    override fun supports(data: RsRemoteSdkAdditionalData): Boolean = data.isWsl

    override fun createSdkEditorDialog(
        project: Project,
        existingSdks: MutableCollection<Sdk>
    ): RsEditRemoteToolchainDialog = RsWslRemoteInterpreterDialog(project, existingSdks)

    override fun createRemoteFileChooser(): RsRemoteFilesChooser = RsWslFileChooser
}
