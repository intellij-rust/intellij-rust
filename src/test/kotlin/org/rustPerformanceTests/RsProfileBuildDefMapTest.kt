/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rustPerformanceTests

import org.rust.lang.core.resolve2.forceRebuildDefMapForAllCrates

class RsProfileBuildDefMapTest : RsPerformanceTestBase() {

    fun `test rustc`() = doTest(RUSTC)

    fun `test Cargo`() = doTest(CARGO)
    fun `test mysql_async`() = doTest(MYSQL_ASYNC)
    fun `test tokio`() = doTest(TOKIO)
    fun `test amethyst`() = doTest(AMETHYST)
    fun `test clap`() = doTest(CLAP)
    fun `test diesel`() = doTest(DIESEL)
    fun `test rust_analyzer`() = doTest(RUST_ANALYZER)
    fun `test xi_editor`() = doTest(XI_EDITOR)
    fun `test juniper`() = doTest(JUNIPER)

    private fun doTest(info: RealProjectInfo) {
        openProject(info)
        profile("buildDefMap") {
            project.forceRebuildDefMapForAllCrates(multithread = false)
        }
    }
}
