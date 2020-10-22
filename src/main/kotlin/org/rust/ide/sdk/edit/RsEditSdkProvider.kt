/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.sdk.edit

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.SdkModificator

/**
 * EP to create custom [RsEditSdkDialog] to edit SDK.
 */
interface RsEditSdkProvider {
    fun isApplicable(modificator: SdkModificator): Boolean

    fun createDialog(
        project: Project,
        modificator: SdkModificator,
        nameValidator: (String) -> String?
    ): RsEditSdkDialog

    companion object {
        private val EP_NAME: ExtensionPointName<RsEditSdkProvider> =
            ExtensionPointName.create("org.rust.editSdkProvider")

        fun createDialog(
            project: Project,
            modificator: SdkModificator,
            nameValidator: (String) -> String?
        ): RsEditSdkDialog? = EP_NAME.extensionList
            .find { it.isApplicable(modificator) }
            ?.createDialog(project, modificator, nameValidator)
    }
}
