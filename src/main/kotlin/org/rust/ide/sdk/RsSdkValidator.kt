/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.sdk

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.projectRoots.Sdk

interface RsSdkValidator {
    fun isInvalid(sdk: Sdk): Boolean

    companion object {
        private val EP_NAME: ExtensionPointName<RsSdkValidator> =
            ExtensionPointName.create("org.rust.sdkValidator")

        fun isInvalid(sdk: Sdk): Boolean = EP_NAME.extensionList.any { it.isInvalid(sdk) }
    }
}
