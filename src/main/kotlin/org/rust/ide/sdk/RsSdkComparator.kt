/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.sdk

import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.util.Comparing
import org.rust.ide.sdk.flavors.RsSdkFlavor
import org.rust.ide.sdk.flavors.RustupSdkFlavor
import java.util.*

object RsSdkComparator : Comparator<Sdk> {
    override fun compare(o1: Sdk, o2: Sdk): Int {
        val type1Weight = if (o1.sdkType is RsSdkType) 1 else 0
        val type2Weight = if (o1.sdkType is RsSdkType) 1 else 0
        if (type1Weight != type2Weight) {
            return -Comparing.compare(o1.name, o2.name)
        }

        val detected1Weight = if (o1 is RsDetectedSdk) 0 else 1
        val detected2Weight = if (o2 is RsDetectedSdk) 0 else 1
        if (detected1Weight != detected2Weight) {
            return detected2Weight - detected1Weight
        }

        val flavor1Weight = if (RsSdkFlavor.getFlavor(o1) is RustupSdkFlavor) 1 else 0
        val flavor2Weight = if (RsSdkFlavor.getFlavor(o2) is RustupSdkFlavor) 1 else 0
        if (flavor1Weight != flavor2Weight) {
            return flavor2Weight - flavor1Weight
        }

        return -Comparing.compare(o1.versionString, o2.versionString)
    }
}
