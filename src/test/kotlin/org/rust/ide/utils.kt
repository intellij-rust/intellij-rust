/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide

import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.util.BuildNumber
import com.intellij.util.PlatformUtils

private val BUILD_231: BuildNumber = BuildNumber.fromString("231")!!

// https://github.com/intellij-rust/intellij-rust/issues/10012
val disableFindUsageTests: Boolean get() {
    @Suppress("UnstableApiUsage", "DEPRECATION")
    return PlatformUtils.isCLion() && ApplicationInfo.getInstance().build >= BUILD_231
}

