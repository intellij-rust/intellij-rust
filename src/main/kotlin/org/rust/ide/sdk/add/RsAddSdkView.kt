/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.sdk.add

import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.ui.ValidationInfo
import java.awt.Component
import javax.swing.Icon

interface RsAddSdkView {
    val panelName: String
    val icon: Icon
    val actions: Map<RsAddSdkDialogFlowAction, Boolean>
    val component: Component
    fun getOrCreateSdk(): Sdk?
    fun onSelected()
    fun previous()
    fun next()
    fun complete()
    fun validateAll(): List<ValidationInfo>
    fun addStateListener(stateListener: RsAddSdkStateListener)
}
