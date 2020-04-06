/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.icons

import com.intellij.icons.AllIcons
import com.intellij.openapi.util.IconLoader

// BACKCOMPAT: 2019.3
@Suppress("UNNECESSARY_NOT_NULL_ASSERTION")
object CargoIcons {
    val ICON = IconLoader.getIcon("/icons/cargo.png")
    val LOCK_ICON = IconLoader.getIcon("/icons/cargo-lock.png")
    val BUILD_RS_ICON = IconLoader.getIcon("/icons/build-rs.svg")
    val EXTERNAL_LINTER = IconLoader.getIcon("/icons/external-linter.svg")
    val TEST = AllIcons.RunConfigurations.TestState.Run!!
    val TEST_GREEN = AllIcons.RunConfigurations.TestState.Green2!!
    val TEST_RED = AllIcons.RunConfigurations.TestState.Red2!!

    // Icons for target nodes in cargo toolwindow
    val TARGETS = IconLoader.getIcon("/icons/targets.svg")
    val BIN_TARGET = IconLoader.getIcon("/icons/target-bin.svg")
    val LIB_TARGET = IconLoader.getIcon("/icons/target-lib.svg")
    val TEST_TARGET = IconLoader.getIcon("/icons/target-test.svg")
    val BENCH_TARGET = IconLoader.getIcon("/icons/target-bench.svg")
    val EXAMPLE_TARGET = IconLoader.getIcon("/icons/target-example.svg")
}
