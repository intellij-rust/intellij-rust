/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.toml.crates.local

import org.rust.cargo.project.model.CargoProject
import org.rust.cargo.project.model.CargoProjectsService
import org.rust.ide.experiments.RsExperiments
import org.rust.openapiext.isFeatureEnabled

class CratesLocalIndexWaker : CargoProjectsService.CargoProjectsListener {
    override fun cargoProjectsUpdated(service: CargoProjectsService, projects: Collection<CargoProject>) {
        if (projects.isNotEmpty() && isFeatureEnabled(RsExperiments.CRATES_LOCAL_INDEX)) {
            // The main job - load the service
            val index = CratesLocalIndexService.getInstance()

            if (index is CratesLocalIndexServiceImpl) {
                index.recoverIfNeeded()
            }
        }
    }
}
