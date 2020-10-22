/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.sdk

import com.intellij.openapi.projectRoots.Sdk
import org.rust.ide.sdk.remote.RsRemoteSdkUtils.isRemoteSdk

object RsLocalSdkValidator : RsSdkValidator {
    override fun isInvalid(sdk: Sdk): Boolean {
        if (isRemoteSdk(sdk)) return false
        val homeDirectory = sdk.homeDirectory
        return homeDirectory == null || !homeDirectory.exists()
    }
}
