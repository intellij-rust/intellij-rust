/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.project.model.impl

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import org.rust.cargo.project.model.CargoProjectsService.CargoProjectsListener
import org.rust.cargo.project.model.CargoProjectsService.Companion.CARGO_PROJECTS_TOPIC
import org.rust.cargo.runconfig.command.workingDirectory
import org.rust.stdext.mapToSet
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap

/**
 * The plugin uses `cargo metadata` command to retrieve project structure from Cargo point of view.
 * It may lead to changes in `Cargo.lock` file.
 * At the same time, the plugin watches changes in `Cargo.lock` to update project structure
 * because the content of `Cargo.lock` is taken into account by Cargo while `cargo metadata` command.
 * As a result, the plugin may call `cargo metadata` twice in a row and the second call always redundant.
 *
 * This service keeps timestamps of previous `cargo metadata` invocation for each cargo project
 * to check changes in `Cargo.lock` should be skipped and avoid unnecessary project loading that can be quite long sometimes
 */
@Service
class CargoEventService(project: Project) {

    private val metadataCallTimestamps: ConcurrentMap<Path, Long> = ConcurrentHashMap()

    init {
        project.messageBus.connect().subscribe(CARGO_PROJECTS_TOPIC, CargoProjectsListener { _, projects ->
            val projectDirs = projects.mapToSet { it.workingDirectory }
            metadataCallTimestamps.keys.retainAll(projectDirs)
        })
    }

    fun onMetadataCall(projectDirectory: Path) {
        metadataCallTimestamps[projectDirectory] = System.currentTimeMillis()
    }

    fun extractTimestamp(projectDirectory: Path): Long? = metadataCallTimestamps.remove(projectDirectory)

    companion object {
        fun getInstance(project: Project): CargoEventService = project.service()
    }
}
