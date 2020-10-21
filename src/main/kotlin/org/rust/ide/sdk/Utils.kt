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
import org.rust.cargo.toolchain.tools.rustup
import org.rust.ide.sdk.flavors.RsSdkFlavor
import org.rust.ide.sdk.flavors.RustupSdkFlavor
import org.rust.ide.sdk.flavors.suggestHomePaths
import org.rust.openapiext.computeWithCancelableProgress
import org.rust.stdext.toPath
import java.nio.file.Path

fun Sdk.modify(action: (SdkModificator) -> Unit) {
    val sdkModificator = sdkModificator
    action(sdkModificator)
    sdkModificator.commitChanges()
}

val Sdk.key: String?
    get() = rustData?.sdkKey

val Sdk.toolchain: RsToolchain?
    get() {
        val homePath = homePath?.toPath() ?: return null
        val toolchainName = rustData?.toolchainName
        return RsToolchain(homePath, toolchainName)
    }

val Sdk.explicitPathToStdlib: String?
    get() = rustData?.explicitPathToStdlib

private val Sdk.rustData: RsSdkAdditionalData?
    get() = sdkAdditionalData as? RsSdkAdditionalData

object RsSdkUtils {

    fun isInvalid(sdk: Sdk): Boolean {
        val toolchain = sdk.homeDirectory
        return toolchain == null || !toolchain.exists()
    }

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

    fun createRustSdkAdditionalData(sdkPath: Path): RsSdkAdditionalData? {
        val data = RsSdkAdditionalData()
        val rustup = RsToolchain(sdkPath, null).rustup()
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
