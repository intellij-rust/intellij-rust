/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.sdk.edit


import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.SdkModificator
import org.rust.ide.sdk.remote.RsRemoteSdkAdditionalData

object RsEditLocalSdkProvider : RsEditSdkProvider {

    override fun isApplicable(modificator: SdkModificator): Boolean {
        val sdkAdditionalData = modificator.sdkAdditionalData ?: return false
        return sdkAdditionalData !is RsRemoteSdkAdditionalData
    }

    override fun createDialog(
        project: Project,
        modificator: SdkModificator,
        nameValidator: (String) -> String?
    ): RsEditSdkDialog = RsEditLocalSdkDialog(
        project,
        modificator,
        nameValidator
    )
}
