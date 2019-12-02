/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.project.model.impl

import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.Consumer
import com.intellij.util.indexing.LightDirectoryIndex
import org.rust.cargo.project.model.CargoProject
import org.rust.cargo.project.model.CargoProjectsService
import org.rust.cargo.project.workspace.CargoWorkspace.Package
import org.rust.openapiext.checkReadAccessAllowed
import org.rust.openapiext.checkWriteAccessAllowed
import java.util.*

class CargoPackageIndex(
    private val project: Project,
    private val service: CargoProjectsService
) : CargoProjectsService.CargoProjectsListener {

    private val indices: MutableMap<CargoProject, LightDirectoryIndex<Optional<Package>>> = hashMapOf()
    private var indexDisposable: Disposable? = null

    init {
        project.messageBus.connect(project).subscribe(CargoProjectsService.CARGO_PROJECTS_TOPIC, this)
    }

    override fun cargoProjectsUpdated(projects: Collection<CargoProject>) {
        checkWriteAccessAllowed()
        resetIndex()
        val disposable = Disposer.newDisposable("CargoPackageIndexDisposable")
        Disposer.register(project, disposable)
        for (cargoProject in projects) {
            val packages = cargoProject.workspace?.packages.orEmpty()
            indices[cargoProject] = LightDirectoryIndex(disposable, Optional.empty(), Consumer { index ->
                for (pkg in packages) {
                    val info = Optional.of(pkg)
                    index.putInfo(pkg.contentRoot, info)
                    index.putInfo(pkg.outDir, info)
                    for (target in pkg.targets) {
                        index.putInfo(target.crateRoot?.parent, info)
                    }
                }
            })
        }
        indexDisposable = disposable
    }

    fun findPackageForFile(file: VirtualFile): Package? {
        checkReadAccessAllowed()
        val cargoProject = service.findProjectForFile(file) ?: return null
        return indices[cargoProject]?.getInfoForFile(file)?.orElse(null)
    }

    private fun resetIndex() {
        val disposable = indexDisposable
        if (disposable != null) {
            Disposer.dispose(disposable)
        }
        indexDisposable = null
        indices.clear()
    }
}
