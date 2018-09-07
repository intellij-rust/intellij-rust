/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.icons

import com.intellij.openapi.util.IconLoader
import javax.swing.Icon

object CargoIcons {
    val ICON: Icon = IconLoader.getIcon("/icons/cargo.png")
    val LOCK_ICON: Icon = IconLoader.getIcon("/icons/cargo-lock.png")
    val BUILD_RS_ICON: Icon = IconLoader.getIcon("/icons/build-rs.svg")
    val CLIPPY: Icon = IconLoader.getIcon("/icons/clippy.svg")

    // Icons for target nodes in cargo toolwindow
    val TARGETS: Icon = IconLoader.getIcon("/icons/targets.svg")
    val BIN_TARGET: Icon = IconLoader.getIcon("/icons/target-bin.svg")
    val LIB_TARGET: Icon = IconLoader.getIcon("/icons/target-lib.svg")
    val TEST_TARGET: Icon = IconLoader.getIcon("/icons/target-test.svg")
    val BENCH_TARGET: Icon = IconLoader.getIcon("/icons/target-bench.svg")
    val EXAMPLE_TARGET: Icon = IconLoader.getIcon("/icons/target-example.svg")
}
