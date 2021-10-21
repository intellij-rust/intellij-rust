/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rustPerformanceTests

import com.intellij.openapi.util.Disposer
import com.sun.management.HotSpotDiagnosticMXBean
import org.rust.lang.core.macros.MacroExpansionScope
import org.rust.lang.core.macros.macroExpansionManager
import org.rust.lang.core.resolve2.getAllDefMaps
import java.lang.management.ManagementFactory

private const val DUMP_HEAP: Boolean = false

class RsBuildDefMapTest : RsRealProjectTestBase() {

    fun `test build rustc`() = doTest(RUSTC)

    fun `test build Cargo`() = doTest(CARGO)
    fun `test build mysql_async`() = doTest(MYSQL_ASYNC)
    fun `test build tokio`() = doTest(TOKIO)
    fun `test build amethyst`() = doTest(AMETHYST)
    fun `test build clap`() = doTest(CLAP)
    fun `test build diesel`() = doTest(DIESEL)
    fun `test build rust_analyzer`() = doTest(RUST_ANALYZER)
    fun `test build xi_editor`() = doTest(XI_EDITOR)
    fun `test build juniper`() = doTest(JUNIPER)

    private fun doTest(info: RealProjectInfo) {
        val disposable = project.macroExpansionManager.setUnitTestExpansionModeAndDirectory(MacroExpansionScope.ALL, name)
        try {
            openRealProject(info)

            val defMaps = project.getAllDefMaps()
            if (DUMP_HEAP) {
                dumpHeap(info.name)
            }
            check(defMaps.isNotEmpty())
        } finally {
            Disposer.dispose(disposable)
        }
    }
}

private fun dumpHeap(name: String) {
    val mxBean = ManagementFactory.newPlatformMXBeanProxy(ManagementFactory.getPlatformMBeanServer(), "com.sun.management:type=HotSpotDiagnostic", HotSpotDiagnosticMXBean::class.java)
    mxBean.dumpHeap("../dumps/$name-${System.currentTimeMillis()}.hprof", true)
}
