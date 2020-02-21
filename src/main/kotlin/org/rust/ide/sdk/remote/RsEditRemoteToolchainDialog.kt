/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.sdk.remote

//import com.intellij.openapi.extensions.ExtensionPointName
//import com.intellij.openapi.project.Project
//import com.intellij.openapi.projectRoots.Sdk
//import org.rust.ide.sdk.remote.RsRemoteSdkAdditionalData
//
//interface RsEditRemoteToolchainDialog {
//    fun setEditing(data: RsRemoteSdkAdditionalData)
//    fun setSdkName(name: String)
//    fun showAndGet(): Boolean
//    fun getSdk(): Sdk
//}
//
//interface RsRemoteSdkEditor {
//    fun supports(data: RsRemoteSdkAdditionalData): Boolean
//    fun createSdkEditorDialog(project: Project, existingSdks: MutableCollection<Sdk>): RsEditRemoteToolchainDialog
//    fun createRemoteFileChooser(): RsRemoteFilesChooser?
//
//    companion object {
//        private val EP: ExtensionPointName<RsRemoteSdkEditor> =
//            ExtensionPointName.create<RsRemoteSdkEditor>("org.rust.remoteSdkEditor")
//
//        fun sdkEditor(
//            data: RsRemoteSdkAdditionalData,
//            project: Project,
//            existingSdks: MutableCollection<Sdk>
//        ): RsEditRemoteToolchainDialog? = forData(data)?.createSdkEditorDialog(project, existingSdks)
//
//        fun filesChooser(data: RsRemoteSdkAdditionalData): RsRemoteFilesChooser? = forData(data)?.createRemoteFileChooser()
//
//        private fun forData(data: RsRemoteSdkAdditionalData): RsRemoteSdkEditor? = EP.extensions.find { it.supports(data) }
//    }
//}
