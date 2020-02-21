/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.sdk

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.rootManager
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.util.UserDataHolder
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.messages.Topic


fun Sdk.isAssociatedWithAnotherModule(module: Module?): Boolean {
    val basePath = module?.basePath ?: return false
    val associatedPath = associatedModulePath ?: return false
    return basePath != associatedPath
}

val Sdk.associatedModulePath: String?
    get() = associatedPathFromAdditionalData


var Project.rustSdk: Sdk?
    get() {
        val sdk = ProjectRootManager.getInstance(this).projectSdk
        return if (sdk?.sdkType is RsSdkType) sdk else null
    }
    set(value) {
        val application = ApplicationManager.getApplication()
        application.invokeAndWait {
            application.runWriteAction {
                ProjectRootManager.getInstance(this).projectSdk = value
            }
        }
    }

var Module.rustSdk: Sdk?
    get() = RsSdkUtils.findRustSdk(this)
    set(value) {
        ModuleRootModificationUtil.setModuleSdk(this, value)
        fireActivePythonSdkChanged(value)
    }

fun Module.fireActivePythonSdkChanged(value: Sdk?): Unit = project
    .messageBus
    .syncPublisher(ActiveSdkListener.ACTIVE_RUST_SDK_TOPIC)
    .activeSdkChanged(this, value)

val Module.baseDir: VirtualFile?
    get() = rootManager.contentRoots.firstOrNull()

val Module.basePath: String?
    get() = baseDir?.path

private val Sdk.associatedPathFromAdditionalData: String?
    get() = (sdkAdditionalData as? RsSdkAdditionalData)?.associatedModulePath

object RsSdkUtils {
    const val RUST_SDK_ID_NAME: String = "Rust SDK"

    fun isRustSdk(sdk: Sdk): Boolean = sdk.sdkType.name == RUST_SDK_ID_NAME

    fun findRustSdk(module: Module?): Sdk? {
        if (module == null) return null
        val sdk = ModuleRootManager.getInstance(module).sdk
        return if (sdk != null && isRustSdk(sdk)) sdk else RsModuleService.getInstance().findRustSdk(module)
    }

    fun getAllSdks(): List<Sdk> = ProjectJdkTable.getInstance().allJdks.filter { sdk -> isRustSdk(sdk) }

    fun findSdkByPath(path: String?): Sdk? = path?.let { findSdkByPath(getAllSdks(), it) }

    fun findSdkByPath(sdkList: List<Sdk?>, path: String?): Sdk? {
        if (path == null) return null
        for (sdk in sdkList) {
            if (sdk != null && FileUtil.pathsEqual(path, sdk.homePath)) {
                return sdk
            }
        }
        return null
    }

    fun findSdkByKey(key: String): Sdk? = ProjectJdkTable.getInstance().findJdk(key)

    fun detectCargoSdks(
        module: Module?,
        existingSdks: List<Sdk>,
        context: UserDataHolder = UserDataHolderBase()
    ): List<RsDetectedSdk> {

    }

    fun detectRustupSdks(
        module: Module?,
        existingSdks: List<Sdk>,
        context: UserDataHolder = UserDataHolderBase()
    ): List<RsDetectedSdk> {

    }

    fun isInvalid(sdk: Sdk): Boolean {
        val interpreter = sdk.homeDirectory
        return interpreter == null || !interpreter.exists()
    }
}

interface ActiveSdkListener {
    fun activeSdkChanged(module: Module, sdk: Sdk?)

    companion object {
        @JvmField
        val ACTIVE_RUST_SDK_TOPIC: Topic<ActiveSdkListener> = Topic("Active SDK changed", ActiveSdkListener::class.java)
    }
}
