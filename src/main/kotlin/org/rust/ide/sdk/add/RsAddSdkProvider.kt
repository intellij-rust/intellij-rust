/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.sdk.add

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.projectRoots.Sdk

interface RsAddSdkProvider {
    /**
     * Returns [RsAddSdkPanel] if applicable.
     */
    fun createPanel(existingSdks: List<Sdk>): RsAddSdkPanel?

    companion object {
        @JvmField
        val EP_NAME: ExtensionPointName<RsAddSdkProvider> = ExtensionPointName.create("org.rust.addSdkProvider")
    }
}
