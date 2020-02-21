/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.wsl.sdk

import com.intellij.openapi.projectRoots.Sdk
import org.rust.ide.sdk.remote.RsRemoteSdkValidator
import org.rust.stdext.Result
import org.rust.wsl.distribution
import org.rust.wsl.isWsl

class RsWslSdkValidator : RsRemoteSdkValidator {
    override fun isInvalid(sdk: Sdk): Boolean = sdk.isWsl && sdk.distribution is Result.Failure
}
