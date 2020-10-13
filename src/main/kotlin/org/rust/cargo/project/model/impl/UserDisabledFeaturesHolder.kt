/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.project.model.impl

import com.intellij.openapi.components.*
import com.intellij.openapi.project.Project
import com.intellij.util.io.systemIndependentPath
import org.jdom.Element
import org.rust.cargo.project.model.cargoProjects
import org.rust.stdext.mapNotNullToSet
import java.nio.file.Path
import java.nio.file.Paths

/**
 * A service needed to store [UserDisabledFeatures] separately from [CargoProjectsServiceImpl].
 * Needed to make it possible to store them in different XML files ([Storage]s)
 */
@State(name = "CargoProjectFeatures", storages = [
    Storage(StoragePathMacros.WORKSPACE_FILE, roamingType = RoamingType.DISABLED)
])
@Service
class UserDisabledFeaturesHolder(private val project: Project) : PersistentStateComponent<Element> {
    private var loadedUserDisabledFeatures: Map<Path, UserDisabledFeatures> = emptyMap()

    fun takeLoadedUserDisabledFeatures(): Map<Path, UserDisabledFeatures> {
        val result = loadedUserDisabledFeatures
        loadedUserDisabledFeatures = emptyMap()
        return result
    }

    override fun getState(): Element {
        val state = Element("state")
        for (cargoProject in project.cargoProjects.allProjects) {
            val pkgToFeatures = cargoProject.userDisabledFeatures
            if (!pkgToFeatures.isEmpty()) {
                val cargoProjectElement = Element("cargoProject")
                cargoProjectElement.setAttribute("file", cargoProject.manifest.systemIndependentPath)
                for ((pkg, features) in pkgToFeatures.pkgRootToDisabledFeatures) {
                    if (features.isNotEmpty()) {
                        val packageElement = Element("package")
                        packageElement.setAttribute("file", pkg.systemIndependentPath)
                        for (feature in features) {
                            val featureElement = Element("feature")
                            featureElement.setAttribute("name", feature)
                            packageElement.addContent(featureElement)
                        }
                        cargoProjectElement.addContent(packageElement)
                    }
                }
                state.addContent(cargoProjectElement)
            }
        }
        return state
    }

    override fun loadState(state: Element) {
        val cargoProjects = state.getChildren("cargoProject")

        loadedUserDisabledFeatures = cargoProjects.associate { cargoProject ->
            val projectFile = cargoProject.getAttributeValue("file").orEmpty()
            val features = UserDisabledFeatures.of(cargoProject.getChildren("package").associate { pkg ->
                val packageFile = pkg.getAttributeValue("file").orEmpty()
                val features = pkg.getChildren("feature").mapNotNullToSet {
                    it.getAttributeValue("name")
                }
                Paths.get(packageFile) to features
            })
            Paths.get(projectFile) to features
        }
    }
}
