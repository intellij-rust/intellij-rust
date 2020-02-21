/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.sdk.remote

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.projectRoots.Sdk

interface RsRemoteSdkValidator {

    fun isInvalid(sdk: Sdk): Boolean

    companion object {
        private val EP: ExtensionPointName<RsRemoteSdkValidator> =
            ExtensionPointName.create<RsRemoteSdkValidator>("org.rust.remoteSdkValidator")

        fun isInvalid(sdk: Sdk): Boolean = EP.extensions.any { it.isInvalid(sdk) }
    }
}
