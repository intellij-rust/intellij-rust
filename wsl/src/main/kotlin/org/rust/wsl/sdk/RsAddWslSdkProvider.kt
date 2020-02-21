/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.wsl.sdk

import com.intellij.execution.wsl.WSLDistributionWithRoot
import com.intellij.execution.wsl.WSLUtil
import com.intellij.icons.AllIcons
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.openapi.util.UserDataHolder
import com.intellij.wsl.WSLCredentialsEditor
import com.intellij.wsl.WSLCredentialsHolder
import com.intellij.wsl.WSLCredentialsType
import org.rust.ide.sdk.add.RsAddSdkProvider
import org.rust.ide.sdk.add.RsAddSdkView
import org.rust.ide.sdk.remote.RsRemoteSdkAdditionalData
import org.rust.remote.RsRemoteToolchainUtils
import org.rust.remote.sdk.RsAddSdkUsingCredentialsEditor
import org.rust.remote.sdk.createAndInitRemoteSdk
import org.rust.stdext.Result
import org.rust.stdext.RsExecutionException
import org.rust.wsl.RsWslPathBrowser
import org.rust.wsl.getDistribution
import org.rust.wsl.toRemotePath
import org.rust.wsl.wslCredentials
import java.awt.BorderLayout
import java.awt.event.ActionListener
import javax.swing.Icon
import javax.swing.JTextPane

private const val url: String = "ms-windows-store://search/?query=Linux"
private const val message: String = "<html>You don't have WSL distribution installed. <a href=\"$url\">Install WSL distributions.</a></html>"

class RsAddWslPanel(existingSdks: List<Sdk>) :
    RsAddSdkUsingCredentialsEditor<WSLCredentialsHolder>(existingSdks, WSLCredentialsType.getInstance()) {
    override val panelName: String = "WSL"
    override val icon: Icon = AllIcons.RunConfigurations.Wsl
    override val credentialsEditor: WSLCredentialsEditor by lazy { WSLCredentialsEditor() }

    init {
        if (!WSLUtil.hasAvailableDistributions()) {
            layout = BorderLayout()
            add(Messages.configureMessagePaneUi(JTextPane(), message), BorderLayout.NORTH)
        } else {
            initUI()
        }
        toolchainPathField.text = "~/.cargo/bin/cargo"
    }

    override fun getHelpersPath(credentials: WSLCredentialsHolder) =
        credentials.distribution?.toRemotePath(RsHelpersLocator.getHelpersRoot().absolutePath)
            ?: super.getHelpersPath(credentials)

    override fun validateAll(): List<ValidationInfo> {
        if (!WSLUtil.hasAvailableDistributions()) {
            return listOf(ValidationInfo("Can't find installed WSL distribution. Make sure you have one."))
        }
        return super.validateAll()
    }

    private val browser = RsWslPathBrowser(toolchainPathField)

    override fun getBrowseButtonActionListener() =
        ActionListener {
            credentialsEditor.wslDistribution?.let { distro ->
                browser.browsePath(WSLDistributionWithRoot(distro), credentialsEditor.mainPanel)
            }
        }

    override fun createSdk(additionalData: RsRemoteSdkAdditionalData) =
        createAndInitRemoteSdk(
            data = additionalData,
            existingSdks = existingSdks,
            suggestedName = validateDataAndSuggestName(additionalData)
        )

    private fun validateDataAndSuggestName(data: RsRemoteSdkAdditionalData): String? {
        val versionString = RsRemoteToolchainUtils.getToolchainVersion(null, data, false)
            ?: throw RsExecutionException("Bad command:", data.interpreterPath)
        val version = data.flavor.getLanguageLevelFromVersionString(versionString)
        return when (val distribution = data.getDistribution()) {
            is Result.Success -> "$version @ ${distribution.result.presentableName}"
            is Result.Failure -> "Error: ${distribution.error}"
        }
    }

    fun configure(data: RsRemoteSdkAdditionalData) {
        toolchainPathField.text = data.interpreterPath
        credentialsEditor.init(data.wslCredentials)
    }
}


class RsAddWslSdkProvider : RsAddSdkProvider {
    override fun createView(
        project: Project?,
        module: Module?,
        newProjectPath: String?,
        existingSdks: List<Sdk>,
        context: UserDataHolder
    ): RsAddSdkView? = if (WSLUtil.isSystemCompatible()) RsAddWslPanel(existingSdks) else null
}
