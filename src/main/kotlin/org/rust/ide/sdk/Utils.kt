/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.sdk

import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.projectRoots.SdkModificator
import com.intellij.openapi.projectRoots.impl.SdkConfigurationUtil
import org.rust.cargo.project.configurable.RsConfigurableToolchainList
import org.rust.cargo.toolchain.RsToolchain
import org.rust.cargo.toolchain.RsToolchainProvider
import org.rust.cargo.toolchain.tools.rustup
import org.rust.ide.sdk.flavors.RsSdkFlavor
import org.rust.ide.sdk.flavors.RustupSdkFlavor
import org.rust.ide.sdk.flavors.suggestHomePaths
import org.rust.openapiext.computeWithCancelableProgress
import org.rust.stdext.toPath

fun Sdk.modify(action: (SdkModificator) -> Unit) {
    val sdkModificator = sdkModificator
    action(sdkModificator)
    sdkModificator.commitChanges()
}

val Sdk.key: String?
    get() = rustData?.sdkKey

val Sdk.toolchain: RsToolchain?
    get() {
        val homePath = homePath ?: return null
        val additionalData = rustData ?: return null
        val toolchainName = additionalData.toolchainName
        return RsToolchainProvider.getToolchain(homePath, toolchainName)
    }

val Sdk.explicitPathToStdlib: String?
    get() = rustData?.explicitPathToStdlib

private val Sdk.rustData: RsSdkAdditionalData?
    get() = sdkAdditionalData as? RsSdkAdditionalData

object RsSdkUtils {

    fun getAllRustSdks(): List<Sdk> = ProjectJdkTable.getInstance().getSdksOfType(RsSdkType.getInstance())

    fun findSdkByKey(key: String): Sdk? = getAllRustSdks().find { it.key == key }

    fun detectRustSdks(existingSdks: List<Sdk>): List<RsDetectedSdk> {
        val existingPaths = existingSdks
            .mapNotNull { it.homePath?.toPath() }
            .filterNot { RustupSdkFlavor.isValidSdkPath(it) }
        return RsSdkFlavor.getApplicableFlavors().asSequence()
            .flatMap { it.suggestHomePaths().asSequence() }
            .map { it.toAbsolutePath() }
            .distinct()
            .filterNot { it in existingPaths }
            .map { RsDetectedSdk(it.toString()) }
            .toList()
    }

    fun findOrCreateSdk(homePath: String? = null): Sdk? {
        if (homePath == null) {
            return SdkConfigurationUtil.findOrCreateSdk(RsSdkComparator, RsSdkType.getInstance())
        }

        val toolchainList = RsConfigurableToolchainList.getInstance(null)
        val existingSdk = toolchainList.allRustSdks.find { it.homePath == homePath }
        if (existingSdk != null) return existingSdk

        return SdkConfigurationUtil.createAndAddSDK(homePath, RsSdkType.getInstance())
    }

    fun createRustSdkAdditionalData(homePath: String): RsSdkAdditionalData? {
        val data = RsSdkAdditionalData()
        val toolchain = RsToolchainProvider.getToolchain(homePath, null) ?: return null
        val rustup = toolchain.rustup()
        if (rustup != null) {
            val project = ProjectManager.getInstance().defaultProject
            // TODO: Fix `Synchronous execution on EDT`
            data.toolchainName = project.computeWithCancelableProgress("Fetching default toolchain...") {
                rustup.listToolchains().find { it.isDefault }?.name
            } ?: return null
        }
        return data
    }
}
