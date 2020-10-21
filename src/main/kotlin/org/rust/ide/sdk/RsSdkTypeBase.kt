/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.sdk

import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.projectRoots.*
import com.intellij.openapi.projectRoots.impl.SdkConfigurationUtil
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.roots.ui.configuration.projectRoot.ProjectSdksModel
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VirtualFile
import org.jdom.Element
import org.rust.cargo.toolchain.RsToolchain
import org.rust.cargo.toolchain.tools.rustc
import org.rust.ide.icons.RsIcons
import org.rust.ide.sdk.RsSdkUtils.createRustSdkAdditionalData
import org.rust.ide.sdk.RsSdkUtils.detectRustSdks
import org.rust.ide.sdk.flavors.RsSdkFlavor
import org.rust.openapiext.computeWithCancelableProgress
import org.rust.stdext.toPath
import javax.swing.Icon

abstract class RsSdkTypeBase : SdkType(RUST_SDK_ID_NAME) {

    override fun getIcon(): Icon = RsIcons.RUST

    override fun getIconForAddAction(): Icon = RsIcons.RUST

    override fun suggestHomePath(): String? = suggestHomePaths().firstOrNull()

    override fun suggestHomePaths(): Collection<String> {
        val existingSdks = ProjectJdkTable.getInstance().allJdks.toList()
        return detectRustSdks(existingSdks).mapNotNull { it.homePath }
    }

    override fun isValidSdkHome(path: String?): Boolean =
        RsSdkFlavor.getFlavor(path?.toPath()) != null

    override fun getHomeChooserDescriptor(): FileChooserDescriptor =
        object : FileChooserDescriptor(false, true, false, false, false, false) {
            override fun validateSelectedFiles(files: Array<VirtualFile>) {
                val selectedPath = files.firstOrNull()?.path ?: return
                if (!isValidSdkHome(selectedPath)) {
                    throw Exception(getInvalidHomeMessage(selectedPath))
                }
            }
        }.withTitle("Select Path for $presentableName").withShowHiddenFiles(SystemInfo.isUnix)

    override fun getHomeFieldLabel(): String = "Toolchain home path:"

    override fun supportsCustomCreateUI(): Boolean = true

    override fun suggestSdkName(currentSdkName: String?, sdkHome: String): String = "Rust"

    // An awful hack
    override fun setupSdkPaths(sdk: Sdk, sdkModel: SdkModel): Boolean {
        val prevAdditionalData = sdk.sdkAdditionalData
        if (prevAdditionalData != null) return true

        val sdkPath = sdk.homePath?.toPath() ?: return false

        val newAdditionalData = createRustSdkAdditionalData(sdkPath) ?: return false
        val suggestedName = buildString {
            append(suggestSdkName(sdk.name, sdkPath.toString()))
            newAdditionalData.toolchainName?.let { append(" ($it)") }
        }
        val newName = SdkConfigurationUtil.createUniqueSdkName(suggestedName, sdkModel.sdks.toList())

        sdk.modify { modificator ->
            modificator.name = newName
            modificator.sdkAdditionalData = newAdditionalData
        }

        return true
    }

    override fun setupSdkPaths(sdk: Sdk) {
        setupSdkPaths(sdk, ProjectSdksModel())
    }

    override fun createAdditionalDataConfigurable(
        sdkModel: SdkModel,
        sdkModificator: SdkModificator
    ): AdditionalDataConfigurable = RsAdditionalDataConfigurable(sdkModel)

    override fun saveAdditionalData(additionalData: SdkAdditionalData, additional: Element) {
        (additionalData as? RsSdkAdditionalData)?.save(additional)
    }

    override fun loadAdditionalData(additional: Element): SdkAdditionalData =
        RsSdkAdditionalData().apply { load(additional) }

    override fun getPresentableName(): String = "Rust Toolchain"

    override fun sdkPath(homePath: VirtualFile): String {
        val path = super.sdkPath(homePath)
        return FileUtil.toSystemDependentName(path)
    }

    override fun getVersionString(sdk: Sdk): String? {
        val toolchain = sdk.toolchain ?: return null
        return getVersionString(toolchain)
    }

    override fun getVersionString(sdkHome: String?): String? {
        val sdkPath = sdkHome?.toPath() ?: return null
        val toolchain = RsToolchain(sdkPath, null)
        return getVersionString(toolchain)
    }

    private fun getVersionString(toolchain: RsToolchain): String? {
        val project = ProjectManager.getInstance().defaultProject
        val rustcVersion = project.computeWithCancelableProgress("Fetching rustc version...") {
            toolchain.rustc().queryVersion()
        }
        return rustcVersion?.semver?.parsedVersion
    }

    // TODO: use [OrderRootType.SOURCES] to store stdlib path
    override fun isRootTypeApplicable(type: OrderRootType): Boolean = false

    override fun sdkHasValidPath(sdk: Sdk): Boolean = sdk.homeDirectory?.isValid ?: false

    companion object {
        const val RUST_SDK_ID_NAME: String = "Rust SDK"
    }
}
