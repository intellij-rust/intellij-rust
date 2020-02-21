/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.remote.sdk

import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.ui.ComponentWithBrowseButton
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.remote.ext.CredentialsEditor
import com.intellij.remote.ext.CredentialsTypeEx
import com.intellij.ui.PanelWithAnchor
import com.intellij.ui.StatusPanel
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.FormBuilder
import org.rust.ide.sdk.add.RsAddSdkPanel
import org.rust.ide.sdk.remote.RsRemoteSdkAdditionalData
import java.awt.BorderLayout
import java.awt.event.ActionListener

abstract class RsAddSdkUsingCredentialsEditor<T>(
    protected val existingSdks: List<Sdk>,
    private val credentialsType: CredentialsTypeEx<T>
) : RsAddSdkPanel() {
    protected val statusPanel: StatusPanel = StatusPanel()
    protected val toolchainPathField: JBTextField = JBTextField("Rust")
    protected abstract val credentialsEditor: CredentialsEditor<T>
    override var sdk: Sdk? = null
        protected set

    override fun onSelected(): Unit = credentialsEditor.onSelected()

    override fun validateAll(): List<ValidationInfo> = credentialsEditor.validate()?.let { listOf(it) }.orEmpty()

    final override fun complete() {
        val sdkAdditionalData = RsRemoteSdkAdditionalData(toolchainPathField.text, false)
        val credentials = credentialsType.createCredentials()
        credentialsEditor.saveCredentials(credentials)
        sdkAdditionalData.setCredentials(credentialsType.credentialsKey, credentials)
        val createAndInitRemoteSdk = createSdk(sdkAdditionalData)
        sdkAdditionalData.helpersPath = getHelpersPath(credentials)
        sdk = createAndInitRemoteSdk
    }

    protected open fun getHelpersPath(credentials: T) = "/opt/.rust_helpers"

    protected open fun createSdk(additionalData: RsRemoteSdkAdditionalData): Sdk =
        createAndInitRemoteSdk(data = additionalData, existingSdks = existingSdks)

    protected fun initUI() {
        layout = BorderLayout()

        val interpreterPathLabel = JBLabel("Rust toolchain path:")

        val form = FormBuilder().addComponent(credentialsEditor.mainPanel)

        val listener = getBrowseButtonActionListener()
        if (listener != null) {
            form.addLabeledComponent(interpreterPathLabel, ComponentWithBrowseButton(toolchainPathField, listener))
        } else {
            form.addLabeledComponent(interpreterPathLabel, toolchainPathField)
        }
        form.addComponent(statusPanel)

        (credentialsEditor as? PanelWithAnchor)?.anchor = interpreterPathLabel

        add(form.panel, BorderLayout.NORTH)
    }

    protected open fun getBrowseButtonActionListener(): ActionListener? = null
}
