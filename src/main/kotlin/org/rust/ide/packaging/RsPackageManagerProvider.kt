/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.packaging

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.projectRoots.Sdk

interface RsPackageManagerProvider {

    fun tryCreateForSdk(sdk: Sdk): RsPackageManager?

    companion object {
        private val LOG: Logger = Logger.getInstance(RsPackageManagerProvider::class.java)
        val EP_NAME: ExtensionPointName<RsPackageManagerProvider> = ExtensionPointName.create("org.rust.packageManagerProvider")

        fun tryCreateCustomPackageManager(sdk: Sdk): RsPackageManager? {
            val managers = EP_NAME.extensionList.mapNotNull { it.safeTryCreateForSdk(sdk) }
            if (managers.size > 1) {
                LOG.warn("Ambiguous Rust package managers found: $managers")
            }
            return managers.firstOrNull()
        }

        private fun RsPackageManagerProvider.safeTryCreateForSdk(sdk: Sdk): RsPackageManager? =
            try {
                tryCreateForSdk(sdk)
            } catch (e: NoClassDefFoundError) {
                LOG.info(e)
                null
            }
    }
}
