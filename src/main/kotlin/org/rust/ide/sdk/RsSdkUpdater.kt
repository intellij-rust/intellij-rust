/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.sdk

import com.intellij.ide.plugins.PluginManagerCore.isUnitTestMode
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.TransactionGuard
import com.intellij.openapi.application.invokeAndWaitIfNeeded
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.projectRoots.SdkModificator
import com.intellij.openapi.startup.StartupActivity
import org.rust.ide.sdk.RsSdkUtils.findSdkByKey
import org.rust.ide.sdk.RsSdkUtils.getAllRustSdks

/**
 * Refreshes all project's Rust SDKs.
 */
class RsSdkUpdater : StartupActivity.Background {
    /**
     * Refreshes the SDKs of the open project after some delay.
     */
    override fun runActivity(project: Project) {
        if (isUnitTestMode || project.isDisposed) return
        ProgressManager.getInstance().run(object : Task.Backgroundable(project, "Updating Rust Toolchains", false) {
            override fun run(indicator: ProgressIndicator) {
                for (sdk in getAllRustSdks()) {
                    updateSdkVersion(sdk)
                }
            }
        })
    }

    companion object {
        /**
         * Changes the version string of an SDK if it's out of date.
         *
         * May be invoked from any thread. May freeze the current thread while evaluating the run-time Rust version.
         */
        fun updateSdkVersion(sdk: Sdk) {
            val modificatorToRead = sdk.sdkModificator
            val versionString = sdk.sdkType.getVersionString(sdk)
            if (versionString != modificatorToRead.versionString) {
                changeSdkModificator(sdk) { modificatorToWrite ->
                    modificatorToWrite.versionString = versionString
                    true
                }
            }
        }

        private fun changeSdkModificator(sdk: Sdk, processor: (SdkModificator) -> Boolean) {
            val sdkKey = checkNotNull(sdk.key) { "Non-Rust SDK" }
            TransactionGuard.getInstance().assertWriteSafeContext(ModalityState.defaultModalityState())
            invokeAndWaitIfNeeded {
                val sdkInsideInvoke = findSdkByKey(sdkKey)
                val effectiveModificator = sdkInsideInvoke?.sdkModificator ?: sdk.sdkModificator
                if (processor(effectiveModificator)) {
                    effectiveModificator.commitChanges()
                }
            }
        }
    }
}
