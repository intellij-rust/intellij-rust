/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.sdk

import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.impl.SdkConfigurationUtil
import com.intellij.openapi.util.Ref
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.DirectoryProjectConfigurator

class RsSdkConfigurator : DirectoryProjectConfigurator {

    override fun configureProject(project: Project, baseDir: VirtualFile, moduleRef: Ref<Module>, newProject: Boolean) {
        SdkConfigurationUtil.configureDirectoryProjectSdk(project, RsPreferredSdkComparator, RsSdkType.getInstance())

//        val sdk = project.rustSdk
//        LOG.debug { "Input: $sdk, $newProject" }
//        if (sdk != null || newProject) return
//
//        ProgressManager.getInstance().run(
//            object : Task.Backgroundable(project, "Configuring a Rust Toolchain", true) {
//                override fun run(indicator: ProgressIndicator) = configureSdk(project, indicator)
//            }
//        )
    }

//    private fun configureSdk(project: Project, indicator: ProgressIndicator) {
//        indicator.isIndeterminate = true
//
//        val context = UserDataHolderBase()
//        val module = ModuleManager.getInstance(project).modules.firstOrNull()
//            .also { LOG.debug { "Module: $it" } }
//            ?: return
//        val existingSdks = ProjectSdksModel().apply { reset(project) }.sdks.filter { it.sdkType is RsSdkType }
//
//        if (indicator.isCanceled) return
//        setTextAndLog(indicator, "Looking for the previously used toolchain")
//        guardIndicator(indicator) { findExistingAssociatedSdk(module, existingSdks) }?.let {
//            LOG.debug { "The previously used toolchain: $it" }
//            onEdt(project) {
//                SdkConfigurationUtil.setDirectoryProjectSdk(project, it)
//                notifyAboutConfiguredSdk(project, module, it)
//            }
//            return
//        }
//
//        if (indicator.isCanceled) return
//        setTextAndLog(indicator, "Looking for the default toolchain setting for a new project")
//        guardIndicator(indicator) { getDefaultProjectSdk() }?.let {
//            LOG.debug { "Default toolchain setting for a new project: $it" }
//            onEdt(project) {
//                SdkConfigurationUtil.setDirectoryProjectSdk(project, it)
//                notifyAboutConfiguredSdk(project, module, it)
//            }
//            return
//        }
//
//        if (indicator.isCanceled) return
//        setTextAndLog(indicator, "Looking for the previously used rustup toolchain")
//        guardIndicator(indicator) { findExistingRustupSdk(existingSdks) }.let {
//            LOG.debug { "Previously used rustup toolchain: $it" }
//            onEdt(project) {
//                SdkConfigurationUtil.setDirectoryProjectSdk(project, it)
//                notifyAboutConfiguredSdk(project, module, it)
//            }
//            return
//        }
//
//        if (indicator.isCanceled) return
//        setTextAndLog(indicator, "Looking for a rustup toolchain")
//        guardIndicator(indicator) { findDetectedRustupSdk(module, existingSdks, context) }.let {
//            LOG.debug { "Detected rustup toolchain: $it" }
//            onEdt(project) {
//                SdkConfigurationUtil.createAndAddSDK(it.homePath!!, RsSdkType.getInstance())?.apply {
//                    LOG.debug { "Created rustup toolchain: $this" }
//                    SdkConfigurationUtil.setDirectoryProjectSdk(project, this)
//                    notifyAboutConfiguredSdk(project, module, this)
//                }
//            }
//        }
//    }
//
//    private fun setTextAndLog(indicator: ProgressIndicator, text: String) {
//        indicator.text = text
//        LOG.debug(text)
//    }
//
//    companion object {
//        private val BALLOON_NOTIFICATIONS: NotificationGroup =
//            NotificationGroup("Rust toolchain configuring", NotificationDisplayType.BALLOON, true)
//
//        private val LOG: Logger = Logger.getInstance(RsSdkConfigurator::class.java)
//
//        private fun findExistingAssociatedSdk(
//            module: Module,
//            existingSdks: List<Sdk>
//        ): Sdk? = existingSdks
//            .asSequence()
//            .filter { it.sdkType is RsSdkType && it.isAssociatedWithModule(module) }
//            .sortedByDescending { it.homePath }
//            .firstOrNull()
//
//        private fun getDefaultProjectSdk(): Sdk? {
//            val manager = ProjectRootManager.getInstance(ProjectManager.getInstance().defaultProject)
//            return manager.projectSdk?.takeIf { it.sdkType is RsSdkType }
//        }
//
//        private fun findExistingRustupSdk(existingSdks: List<Sdk>) =
//            filterRustupSdks(existingSdks).sortedWith(RsPreferredSdkComparator).firstOrNull()
//
//        private fun findDetectedRustupSdk(module: Module?, existingSdks: List<Sdk>, context: UserDataHolder) =
//            detectRustupSdks(module, existingSdks, context).firstOrNull()
//
//        private fun <T> guardIndicator(indicator: ProgressIndicator, computable: () -> T): T =
//            ProgressManager.getInstance().runProcess(computable, SensitiveProgressWrapper(indicator))
//
//        private fun onEdt(project: Project, runnable: () -> Unit) = AppUIUtil.invokeOnEdt(Runnable { runnable() }, project.disposed)
//
//        private fun notifyAboutConfiguredSdk(project: Project, module: Module, sdk: Sdk) {
//            BALLOON_NOTIFICATIONS.createNotification(
//                "${sdk.name} has been configured as the project toolchain",
//                NotificationType.INFORMATION
//            ).apply {
//                val configureSdkAction = NotificationAction.createSimpleExpiring("Configure a Rust Toolchain...") {
//                    RsSdkPopupFactory.createAndShow(project, module)
//                }
//
//                addAction(configureSdkAction)
//                notify(project)
//            }
//        }
//    }
}
