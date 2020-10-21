/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.sdk

import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.projectRoots.impl.ProjectJdkImpl
import com.intellij.openapi.projectRoots.impl.SdkConfigurationUtil

class RsDetectedSdk(homePath: String) : ProjectJdkImpl(homePath, RsSdkType.getInstance()) {

    init {
        this.homePath = homePath
    }

    override fun getVersionString(): String? = ""

    fun setup(existingSdks: List<Sdk>, additionalData: RsSdkAdditionalData): Sdk? {
        val sdkType = RsSdkType.getInstance()
        val suggestedName = buildString {
            append(sdkType.suggestSdkName(null, homePath ?: return null))
            additionalData.toolchainName?.let { append(" ($it)") }
        }
        return SdkConfigurationUtil.setupSdk(
            existingSdks.toTypedArray(),
            homeDirectory ?: return null,
            sdkType,
            false,
            additionalData,
            suggestedName
        )
    }
}
