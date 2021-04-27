/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rustPerformanceTests

import org.rust.cargo.project.model.impl.testCargoProjects
import org.rust.lang.core.resolve2.getAllDefMaps

class RsProfileCargoRefreshTest : RsPerformanceTestBase() {

    fun `test Cargo`() = profile(CARGO)

    private fun profile(info: RealProjectInfo) {
        openProject(info)

        profile("Cargo refresh") {
            project.testCargoProjects.refreshAllProjectsSync()
            project.getAllDefMaps()
        }
    }
}
