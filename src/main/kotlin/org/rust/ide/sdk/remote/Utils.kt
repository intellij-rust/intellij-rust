/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.sdk.remote

import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.util.Ref
import com.intellij.remote.ext.LanguageCaseCollector

object RsRemoteSdkUtils {
    private val CUSTOM_RUST_SDK_HOME_PATH_PATTERN: Regex = "[-a-zA-Z_0-9]{2,}:.*".toRegex()

    fun isRemoteSdk(sdk: Sdk): Boolean = sdk.sdkAdditionalData is RsRemoteSdkAdditionalData

    /**
     * Returns whether provided Rust toolchain path corresponds to custom Rust SDK.
     *
     * @param homePath SDK home path
     * @return whether provided Rust toolchain path corresponds to Rust SDK
     */
    fun isCustomSdkHomePath(homePath: String): Boolean =
        CUSTOM_RUST_SDK_HOME_PATH_PATTERN.matches(homePath)

    fun isIncompleteRemote(sdk: Sdk): Boolean {
        if (!isRemoteSdk(sdk)) return false
        val additionalData = sdk.sdkAdditionalData as? RsRemoteSdkAdditionalData ?: return true
        return additionalData.isValid
    }

    fun hasInvalidRemoteCredentials(sdk: Sdk): Boolean {
        if (!isRemoteSdk(sdk)) return false
        val additionalData = sdk.sdkAdditionalData as? RsRemoteSdkAdditionalData ?: return false
        val result = Ref.create(false)
        additionalData.switchOnConnectionType(
            *object : LanguageCaseCollector<RsCredentialsContribution>() {
                override fun processLanguageContribution(
                    languageContribution: RsCredentialsContribution,
                    credentials: Any?
                ) {
                    result.set(credentials == null)
                }
            }.collectCases(RsCredentialsContribution::class.java)
        )
        return result.get()
    }
}
