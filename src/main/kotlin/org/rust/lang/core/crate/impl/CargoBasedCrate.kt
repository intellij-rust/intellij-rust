/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.crate.impl

import com.intellij.openapi.vfs.VirtualFile
import org.rust.cargo.CfgOptions
import org.rust.cargo.project.model.CargoProject
import org.rust.cargo.project.workspace.CargoWorkspace
import org.rust.cargo.project.workspace.FeatureState
import org.rust.cargo.project.workspace.PackageOrigin
import org.rust.lang.core.crate.Crate
import org.rust.lang.core.crate.CratePersistentId
import org.rust.lang.core.psi.RsFile
import org.rust.lang.core.psi.rustFile
import org.rust.openapiext.fileId
import org.rust.openapiext.toPsiFile
import java.util.*

class CargoBasedCrate(
    override var cargoProject: CargoProject,
    override var cargoTarget: CargoWorkspace.Target,
    override val dependencies: Collection<Crate.Dependency>,
    override val flatDependencies: LinkedHashSet<Crate>
) : Crate {
    override val reverseDependencies = mutableListOf<CargoBasedCrate>()
    override var features: Map<String, FeatureState> = cargoTarget.pkg.featureState

    // These properties are fields (not just delegates to `cargoTarget`) because [Crate] must be immutable
    override val rootModFile: VirtualFile? = cargoTarget.crateRoot
    override val id: CratePersistentId? = rootModFile?.fileId

    /** See docs for [org.rust.lang.core.crate.CrateGraphService] */
    var cyclicDevDeps: List<Crate.Dependency> = emptyList()

    override val dependenciesWithCyclic: Collection<Crate.Dependency>
        get() = dependencies + cyclicDevDeps

    init {
        for (dependency in dependencies) {
            (dependency.crate as CargoBasedCrate).reverseDependencies += this
        }
    }

    override val cargoWorkspace: CargoWorkspace get() = cargoTarget.pkg.workspace
    override val kind: CargoWorkspace.TargetKind get() = cargoTarget.kind

    override val cfgOptions: CfgOptions get() = cargoTarget.pkg.cfgOptions
    override val env: Map<String, String> get() = cargoTarget.pkg.env
    override val outDir: VirtualFile? get() = cargoTarget.pkg.outDir

    override val rootMod: RsFile? get() = rootModFile?.toPsiFile(cargoProject.project)?.rustFile

    override val origin: PackageOrigin get() = cargoTarget.pkg.origin
    override val edition: CargoWorkspace.Edition get() = cargoTarget.edition
    override val areDoctestsEnabled: Boolean get() = cargoTarget.doctest && cargoTarget.isDoctestable
    override val presentableName: String get() = cargoTarget.name
    override val normName: String get() = cargoTarget.normName

    override fun toString(): String = "${cargoTarget.name}(${kind.name})"
}

// See https://github.com/rust-lang/cargo/blob/5a0c31d81/src/cargo/core/manifest.rs#L775
private val CargoWorkspace.Target.isDoctestable: Boolean
    get() {
        val kind = kind as? CargoWorkspace.TargetKind.Lib ?: return false
        return CargoWorkspace.LibKind.LIB in kind.kinds ||
            CargoWorkspace.LibKind.RLIB in kind.kinds ||
            CargoWorkspace.LibKind.PROC_MACRO in kind.kinds
    }
