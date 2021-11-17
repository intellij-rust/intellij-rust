/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.crate.impl

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.openapi.vfs.VirtualFile
import org.rust.cargo.CfgOptions
import org.rust.cargo.project.model.CargoProject
import org.rust.cargo.project.workspace.CargoWorkspace
import org.rust.cargo.project.workspace.CargoWorkspaceData
import org.rust.cargo.project.workspace.FeatureState
import org.rust.cargo.project.workspace.PackageOrigin
import org.rust.lang.core.crate.Crate
import org.rust.lang.core.crate.CratePersistentId
import org.rust.lang.core.psi.RsFile

/** Fake crate for [RsFile] outside of module tree. */
class FakeDetachedCrate(
    override val rootMod: RsFile,
    override val id: CratePersistentId,
    override val dependencies: Collection<Crate.Dependency>,
) : UserDataHolderBase(), Crate {
    override val flatDependencies: LinkedHashSet<Crate> = dependencies.flattenTopSortedDeps()

    override val reverseDependencies: List<Crate> get() = emptyList()

    override val cargoProject: CargoProject? get() = null
    override val cargoTarget: CargoWorkspace.Target? get() = null
    override val cargoWorkspace: CargoWorkspace? get() = null
    override val kind: CargoWorkspace.TargetKind get() = CargoWorkspace.TargetKind.Test

    override val cfgOptions: CfgOptions get() = CfgOptions.EMPTY
    override val features: Map<String, FeatureState> get() = emptyMap()
    override val evaluateUnknownCfgToFalse: Boolean get() = true
    override val env: Map<String, String> get() = emptyMap()
    override val outDir: VirtualFile? get() = null

    override val rootModFile: VirtualFile? get() = rootMod.virtualFile
    override val origin: PackageOrigin get() = PackageOrigin.WORKSPACE
    override val edition: CargoWorkspace.Edition get() = CargoWorkspace.Edition.values().last()
    override val areDoctestsEnabled: Boolean get() = false
    override val presentableName: String get() = "Fake for ${rootModFile?.path}"
    override val normName: String get() = "__fake__"
    override val project: Project get() = rootMod.project
    override val procMacroArtifact: CargoWorkspaceData.ProcMacroArtifact? get() = null

    override fun toString(): String = presentableName
}
