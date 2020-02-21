/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.sdk

import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.projectRoots.impl.ProjectJdkImpl
import com.intellij.openapi.projectRoots.impl.SdkConfigurationUtil
import com.intellij.util.PathUtil

fun RsDetectedSdk.setup(existingSdks: List<Sdk>): Sdk? {
    val homeDir = homeDirectory ?: return null
    return SdkConfigurationUtil.setupSdk(existingSdks.toTypedArray(), homeDir, RsSdkType.getInstance(), false, null, null)
}

fun RsDetectedSdk.setupAssociated(existingSdks: List<Sdk>, associatedModulePath: String?): Sdk? {
    val homeDir = homeDirectory ?: return null
    val suggestedName = homePath?.let { suggestAssociatedSdkName(it, associatedModulePath) }
    return SdkConfigurationUtil.setupSdk(existingSdks.toTypedArray(), homeDir, RsSdkType.getInstance(), false, null, suggestedName)
}

private fun suggestAssociatedSdkName(sdkHome: String, associatedPath: String?): String? {
    val baseSdkName = RsSdkType.suggestBaseSdkName(sdkHome) ?: return null
    val associatedName = associatedPath?.let { PathUtil.getFileName(associatedPath) } ?: return null
    return "$baseSdkName ($associatedName)"
}

class RsDetectedSdk(name: String) : ProjectJdkImpl(name, RsSdkType.getInstance()) {

    init {
        homePath = name
    }

    override fun getVersionString(): String? = ""
}
