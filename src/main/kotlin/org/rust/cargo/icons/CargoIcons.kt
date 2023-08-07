/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.icons

import com.intellij.icons.AllIcons
import com.intellij.openapi.util.IconLoader
import javax.swing.Icon

object CargoIcons {
    val ICON = load("/icons/cargo.svg")
    val LOCK_ICON = load("/icons/cargoLock.svg")
    val BUILD_RS_ICON = load("/icons/rustBuild.svg")
    val TEST = AllIcons.RunConfigurations.TestState.Run
    val TEST_GREEN = AllIcons.RunConfigurations.TestState.Green2
    val TEST_RED = AllIcons.RunConfigurations.TestState.Red2

    // Icons for target nodes in cargo toolwindow
    val TARGETS = load("/icons/targets.svg")
    val BIN_TARGET = load("/icons/targetBin.svg")
    val LIB_TARGET = load("/icons/targetLib.svg")
    val TEST_TARGET = load("/icons/targetTest.svg")
    val BENCH_TARGET = load("/icons/targetBench.svg")
    val EXAMPLE_TARGET = load("/icons/targetExample.svg")
    val CUSTOM_BUILD_TARGET = load("/icons/targetCustomBuild.svg")

    val BSP = load("/icons/bsp/bsp.svg")
    val RUST = load("/icons/rust.svg")

    val RELOAD_ICON = load("/icons/rustReload.svg")

    private fun load(path: String): Icon = IconLoader.getIcon(path, CargoIcons::class.java)
}
