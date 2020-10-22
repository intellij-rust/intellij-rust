/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.sdk.edit

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import org.rust.ide.sdk.RsSdkAdditionalData

/**
 * Injected by [RsEditSdkProvider] to edit a toolchain.
 */
abstract class RsEditSdkDialog(project: Project) : DialogWrapper(project, true) {
    abstract val sdkName: String
    abstract val sdkHomePath: String?
    abstract val sdkAdditionalData: RsSdkAdditionalData?
}
