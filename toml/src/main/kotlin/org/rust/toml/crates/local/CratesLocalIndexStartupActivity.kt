/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.toml.crates.local

import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.StartupActivity
import org.rust.cargo.project.model.cargoProjects
import org.rust.ide.experiments.RsExperiments
import org.rust.openapiext.isFeatureEnabled
import org.rust.openapiext.isUnitTestMode

/**
 * [CratesLocalIndexService] initializer.
 * Tries to get and initialize a service instance and updates it if it is needed.
 */
class CratesLocalIndexStartupActivity : StartupActivity.Background {
    override fun runActivity(project: Project) {
        if (isUnitTestMode) return
        if (!project.cargoProjects.hasAtLeastOneValidProject) return

        if (isFeatureEnabled(RsExperiments.CRATES_LOCAL_INDEX)) {
            CratesLocalIndexService.getInstance().updateIfNeeded()
        }
    }
}
