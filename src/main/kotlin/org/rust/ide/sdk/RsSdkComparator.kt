/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.sdk

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.projectRoots.Sdk

interface RsSdkComparator {
    fun compare(sdk1: Sdk?, sdk2: Sdk?): Int

    companion object {
        val EP_NAME: ExtensionPointName<RsSdkComparator> = ExtensionPointName.create("org.rust.rustSdkComparator")
    }
}
