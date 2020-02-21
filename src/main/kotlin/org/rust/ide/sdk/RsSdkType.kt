/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.sdk

import com.intellij.ide.DataManager
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.intellij.openapi.projectRoots.*
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.Consumer
import org.jdom.Element
import org.rust.ide.icons.RsIcons
import org.rust.ide.sdk.RsSdkUtils.detectCargoSdks
import org.rust.ide.sdk.RsSdkUtils.detectRustupSdks
import org.rust.ide.sdk.add.RsAddSdkDialog
import org.rust.ide.sdk.flavors.RsSdkFlavor
import java.lang.ref.WeakReference
import javax.swing.Icon
import javax.swing.JComponent

class RsSdkType : SdkType(RUST_SDK_ID_NAME) {

    override fun getIcon(): Icon = RsIcons.RUST

    override fun getHelpTopic(): String {
        return super.getHelpTopic() // TODO
    }

    override fun getIconForAddAction(): Icon = RsIcons.RUST_FILE

    override fun suggestHomePath(): String? {
        val existingSdks = ProjectJdkTable.getInstance().allJdks.toList()
        val sdks = detectRustupSdks(null, existingSdks) + detectCargoSdks(null, existingSdks)
        return sdks.firstOrNull()?.homePath
    }

    override fun isValidSdkHome(path: String?): Boolean = RsSdkFlavor.getFlavor(path) != null

    override fun getHomeChooserDescriptor(): FileChooserDescriptor =
        object : FileChooserDescriptor(true, false, false, false, false, false) {
            override fun validateSelectedFiles(files: Array<VirtualFile>) {
                if (files.isNotEmpty() && !isValidSdkHome(files.first().path)) {
                    throw Exception("Invalid Rust toolchain name '${files.first().name}'")
                }
            }

            override fun isFileVisible(file: VirtualFile, showHiddenFiles: Boolean): Boolean {
                if (!file.isDirectory && SystemInfo.isWindows) {
                    return file.path.endsWith("exe") && super.isFileVisible(file, showHiddenFiles)
                }
                return super.isFileVisible(file, showHiddenFiles)
            }
        }.withTitle("Select Rust Toolchain").withShowHiddenFiles(SystemInfo.isUnix)

    override fun supportsCustomCreateUI(): Boolean = true

    override fun showCustomCreateUI(sdkModel: SdkModel, parentComponent: JComponent, sdkCreatedCallback: Consumer<Sdk>) {
        val project = CommonDataKeys.PROJECT.getData(DataManager.getInstance().getDataContext(parentComponent))
        RsAddSdkDialog.show(project, null, sdkModel.sdks.toList()) { sdk ->
            if (sdk != null) {
                sdk.putUserData(SDK_CREATOR_COMPONENT_KEY, WeakReference(parentComponent))
                sdkCreatedCallback.consume(sdk)
            }
        }
    }

    override fun suggestSdkName(currentSdkName: String?, sdkHome: String): String =
        suggestBaseSdkName(sdkHome) ?: "Unknown"

    override fun createAdditionalDataConfigurable(
        sdkModel: SdkModel,
        sdkModificator: SdkModificator
    ): AdditionalDataConfigurable? = null

    override fun saveAdditionalData(additionalData: SdkAdditionalData, additional: Element) {
        (additionalData as? RsSdkAdditionalData)?.save(additional)
    }

    override fun loadAdditionalData(currentSdk: Sdk, additional: Element): SdkAdditionalData? =
        RsSdkAdditionalData.load(currentSdk, additional)

    override fun getPresentableName(): String = RUST_SDK_ID_NAME

    override fun sdkPath(homePath: VirtualFile): String {
        val path = super.sdkPath(homePath)
        val flavor = RsSdkFlavor.getFlavor(path)
        if (flavor != null) {
            val sdkPath = flavor.getSdkPath(homePath)
            if (sdkPath != null) {
                return FileUtil.toSystemDependentName(sdkPath.path)
            }
        }
        return FileUtil.toSystemDependentName(path)
    }

    override fun getVersionString(sdkHome: String?): String? {
        val flavor = RsSdkFlavor.getFlavor(sdkHome)
        return flavor?.getVersionString(sdkHome)
    }

    override fun isRootTypeApplicable(type: OrderRootType): Boolean = type === OrderRootType.CLASSES

    override fun sdkHasValidPath(sdk: Sdk): Boolean = sdk.homeDirectory?.isValid ?: false

    companion object {
        const val RUST_SDK_ID_NAME: String = "Rust SDK"

        private val SDK_CREATOR_COMPONENT_KEY: Key<WeakReference<JComponent>> =
            Key.create<WeakReference<JComponent>>("#org.rust.ide.sdk.creatorComponent")

        fun getInstance(): RsSdkType = findInstance(RsSdkType::class.java)

        fun getSdkKey(sdk: Sdk): String = sdk.name

        fun suggestBaseSdkName(sdkHome: String): String? = RsSdkFlavor.getFlavor(sdkHome)?.name
    }
}
