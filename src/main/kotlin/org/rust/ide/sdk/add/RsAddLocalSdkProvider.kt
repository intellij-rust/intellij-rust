/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.sdk.add

import com.intellij.openapi.projectRoots.Sdk

object RsAddLocalSdkProvider : RsAddSdkProvider {
    override fun createPanel(existingSdks: List<Sdk>): RsAddSdkPanel = RsAddLocalSdkPanel(existingSdks)
}
