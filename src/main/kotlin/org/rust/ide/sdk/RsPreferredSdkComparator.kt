/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.sdk

import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.util.Comparing
import java.util.*

object RsPreferredSdkComparator : Comparator<Sdk> {
    override fun compare(o1: Sdk, o2: Sdk): Int {
        for (comparator in RsSdkComparator.EP_NAME.extensionList) {
            val result: Int = comparator.compare(o1, o2)
            if (result != 0) {
                return result
            }
        }

        // TODO: Rustup > Cargo

        val detectedWeight1 = if (o1 is RsDetectedSdk) 0 else 1
        val detectedWeight2 = if (o2 is RsDetectedSdk) 0 else 1
        if (detectedWeight1 != detectedWeight2) {
            return detectedWeight2 - detectedWeight1
        }

        return -Comparing.compare(o1.versionString, o2.versionString)
    }
}
