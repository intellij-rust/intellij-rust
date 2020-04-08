/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.crate.impl

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.SimpleModificationTracker
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.VirtualFileWithId
import com.intellij.openapiext.isUnitTestMode
import com.intellij.psi.util.CachedValueProvider
import com.intellij.util.containers.addIfNotNull
import gnu.trove.TIntObjectHashMap
import org.rust.cargo.project.model.CargoProject
import org.rust.cargo.project.model.CargoProjectsService
import org.rust.cargo.project.model.CargoProjectsService.CargoProjectsListener
import org.rust.cargo.project.model.CargoProjectsService.Companion.CARGO_PROJECTS_TOPIC
import org.rust.cargo.project.model.cargoProjects
import org.rust.cargo.project.workspace.CargoWorkspace
import org.rust.cargo.project.workspace.FeatureState
import org.rust.cargo.project.workspace.PackageOrigin
import org.rust.lang.core.crate.Crate
import org.rust.lang.core.crate.CrateGraphService
import org.rust.lang.core.crate.CratePersistentId
import org.rust.openapiext.CachedValueDelegate
import org.rust.openapiext.checkReadAccessAllowed
import org.rust.stdext.applyWithSymlink
import org.rust.stdext.enumSetOf
import org.rust.stdext.exhaustive
import java.nio.file.Path

class CrateGraphServiceImpl(val project: Project) : CrateGraphService {

    private val cargoProjectsModTracker = SimpleModificationTracker()

    init {
        project.messageBus.connect().subscribe(CARGO_PROJECTS_TOPIC, object : CargoProjectsListener {
            override fun cargoProjectsUpdated(service: CargoProjectsService, projects: Collection<CargoProject>) {
                cargoProjectsModTracker.incModificationCount()
            }
        })
    }

    private val crateGraph: CrateGraph by CachedValueDelegate {
        val result = buildCrateGraph(project, project.cargoProjects.allProjects)
        CachedValueProvider.Result(result, cargoProjectsModTracker, VirtualFileManager.VFS_STRUCTURE_MODIFICATIONS)
    }

    override val topSortedCrates: List<Crate>
        get() {
            checkReadAccessAllowed()
            return crateGraph.topSortedCrates
        }

    override fun findCrateById(id: CratePersistentId): Crate? {
        checkReadAccessAllowed()
        return crateGraph.idToCrate.get(id)
    }

    override fun findCrateByRootMod(rootModFile: VirtualFile): Crate? {
        checkReadAccessAllowed()
        return rootModFile.applyWithSymlink { if (it is VirtualFileWithId) findCrateById(it.id) else null }
    }

}

private data class CrateGraph(
    val topSortedCrates: List<Crate>,
    val idToCrate: TIntObjectHashMap<Crate>
)

private val LOG = Logger.getInstance(CrateGraphServiceImpl::class.java)

private fun buildCrateGraph(project: Project, cargoProjects: Collection<CargoProject>): CrateGraph {
    val builder = CrateGraphBuilder(project)
    for (cargoProject in cargoProjects) {
        val workspace = cargoProject.workspace ?: continue
        for (pkg in workspace.packages) {
            try {
                builder.lowerPackage(ProjectPackage(cargoProject, pkg))
            } catch (e: CyclicGraphException) {
                // This should not occur, but if it is, let's just log the exception instead of breaking everything
                LOG.error(e)
            }
        }
    }
    return builder.build()
}

private class CrateGraphBuilder(val project: Project) {
    private val states = hashMapOf<Path, NodeState>()
    private val topSortedCrates = mutableListOf<CargoBasedCrate>()

    private val cratesToLowerLater = mutableListOf<NonLibraryCrates>()
    private val cratesToReplaceTargetLater = mutableListOf<ReplaceProjectAndTarget>()

    fun lowerPackage(pkg: ProjectPackage): CargoBasedCrate? {
        when (val state = states[pkg.rootDirectory]) {
            is NodeState.Done -> {
                val libCrate = state.libCrate
                if (state.pkgs.add(pkg.pkg)) {
                    // Duplicated package found. This can occur if a package is used in multiple CargoProjects.
                    // Merging them into a single crate
                    if (libCrate != null) {
                        libCrate.features = mergeFeatures(pkg.pkg.featureState, libCrate.features)
                    }

                    // Prefer workspace target
                    if (pkg.pkg.origin == PackageOrigin.WORKSPACE) {
                        cratesToReplaceTargetLater += ReplaceProjectAndTarget(state, pkg)
                    }
                }
                return libCrate
            }

            NodeState.Processing -> throw CyclicGraphException(pkg.pkg.name)

            else -> states[pkg.rootDirectory] = NodeState.Processing
        }

        val (buildDeps, normalAndNonCyclicDevDeps, cyclicDevDependencies) = lowerPackageDependencies(pkg)

        val customBuildCrate = pkg.pkg.customBuildTarget?.let { target ->
            CargoBasedCrate(pkg.project, target, buildDeps, buildDeps.flattenTopSortedDeps())
        }
        topSortedCrates.addIfNotNull(customBuildCrate)

        val flatNormalAndNonCyclicDevDeps = normalAndNonCyclicDevDeps.flattenTopSortedDeps()
        val libCrate = pkg.pkg.libTarget?.let { libTarget ->
            CargoBasedCrate(pkg.project, libTarget, normalAndNonCyclicDevDeps, flatNormalAndNonCyclicDevDeps)
        }

        val newState = NodeState.Done(libCrate)
        newState.pkgs += pkg.pkg
        newState.nonLibraryCrates.addIfNotNull(customBuildCrate)

        states[pkg.rootDirectory] = newState
        topSortedCrates.addIfNotNull(libCrate)

        lowerNonLibraryCratesLater(NonLibraryCrates(
            pkg,
            newState,
            normalAndNonCyclicDevDeps,
            cyclicDevDependencies,
            flatNormalAndNonCyclicDevDeps
        ))

        return libCrate
    }

    private class ReplaceProjectAndTarget(
        val state: NodeState.Done,
        val pkg: ProjectPackage
    )

    private fun ReplaceProjectAndTarget.replaceProjectAndTarget() {
        val libCrate = state.libCrate
        if (libCrate != null) {
            pkg.pkg.libTarget?.let {
                libCrate.cargoTarget = it
                libCrate.cargoProject = pkg.project
            }
        }
        for (crate in state.nonLibraryCrates) {
            val newTarget = pkg.pkg.targets.find { it.name == crate.cargoTarget.name }
                ?: continue
            crate.cargoTarget = newTarget
            crate.cargoProject = pkg.project
        }
    }

    private data class LoweredPackageDependencies(
        val buildDeps: List<Crate.Dependency>,
        val normalAndNonCyclicDevDeps: List<Crate.Dependency>,
        val cyclicDevDependencies: List<CargoWorkspace.Dependency>
    )

    private fun lowerPackageDependencies(pkg: ProjectPackage): LoweredPackageDependencies {
        return when (val dependencies = pkg.pkg.dependencies.classify()) {
            is SplitDependencies.Classified -> {
                val buildDeps = lowerDependencies(dependencies.build, pkg)
                val normalDeps = lowerDependencies(dependencies.normal, pkg)
                val (nonCyclicDevDeps, cyclicDevDependencies) = lowerDependenciesWithCycles(dependencies.dev, pkg)
                val normalAndNonCyclicDevDeps = normalDeps + nonCyclicDevDeps
                LoweredPackageDependencies(buildDeps, normalAndNonCyclicDevDeps, cyclicDevDependencies)
            }
            is SplitDependencies.Unclassified -> {
                // Here we can't distinguish normal/dev/build dependencies because of old Cargo (prior to `1.41.0`).
                //
                val (lowered, cyclic) = lowerDependenciesWithCycles(dependencies.dependencies, pkg)
                LoweredPackageDependencies(lowered, lowered, cyclic)
            }
        }
    }

    private sealed class SplitDependencies {
        data class Classified(
            val normal: Collection<CargoWorkspace.Dependency>,
            val dev: Collection<CargoWorkspace.Dependency>,
            val build: Collection<CargoWorkspace.Dependency>
        ) : SplitDependencies()

        // For old Cargo versions prior to `1.41.0`
        data class Unclassified(val dependencies: Collection<CargoWorkspace.Dependency>) : SplitDependencies()
    }

    private fun Collection<CargoWorkspace.Dependency>.classify(): SplitDependencies {
        val unclassified = mutableListOf<CargoWorkspace.Dependency>()
        val normal = mutableListOf<CargoWorkspace.Dependency>()
        val dev = mutableSetOf<CargoWorkspace.Dependency>()
        val build = mutableListOf<CargoWorkspace.Dependency>()

        for (dependency in this) {
            val visitedDepKinds = enumSetOf<CargoWorkspace.DepKind>()

            for (depKind in dependency.depKinds) {
                // `cargo metadata` sometimes duplicate `depKinds` for some reason
                if (!visitedDepKinds.add(depKind.kind)) continue

                when (depKind.kind) {
                    CargoWorkspace.DepKind.Stdlib -> {
                        normal += dependency
                        build += dependency
                        // No `dev += dependency` because all crates that use `dev` dependencies also use
                        // `normal` dependencies
                    }
                    CargoWorkspace.DepKind.Unclassified -> unclassified += dependency
                    CargoWorkspace.DepKind.Normal -> normal += dependency
                    CargoWorkspace.DepKind.Development -> dev += dependency
                    CargoWorkspace.DepKind.Build -> build += dependency
                }.exhaustive
            }
        }

        dev.removeAll(normal)

        return if (unclassified.isNotEmpty()) {
            // If these is at least one unclassified dependency, then we're on old Cargo and all dependencies
            // are unclassified
            SplitDependencies.Unclassified(unclassified + normal)
        } else {
            SplitDependencies.Classified(normal, dev, build)
        }
    }

    private fun lowerDependencies(
        deps: Iterable<CargoWorkspace.Dependency>,
        pkg: ProjectPackage
    ): List<Crate.Dependency> {
        return try {
            deps.mapNotNull { dep ->
                lowerPackage(ProjectPackage(pkg.project, dep.pkg))?.let { Crate.Dependency(dep.name, it) }
            }
        } catch (e: CyclicGraphException) {
            states.remove(pkg.rootDirectory)
            e.pushCrate(pkg.pkg.name)
            throw e
        }
    }

    private data class LoweredAndCyclicDependencies(
        val lowered: List<Crate.Dependency>,
        val cyclic: List<CargoWorkspace.Dependency>
    )

    private fun lowerDependenciesWithCycles(
        devDependencies: Collection<CargoWorkspace.Dependency>,
        pkg: ProjectPackage
    ): LoweredAndCyclicDependencies {
        val cyclic = mutableListOf<CargoWorkspace.Dependency>()
        val lowered = devDependencies.mapNotNull { dep ->
            try {
                lowerPackage(ProjectPackage(pkg.project, dep.pkg))?.let { Crate.Dependency(dep.name, it) }
            } catch (ignored: CyclicGraphException) {
                // This can occur because `dev-dependencies` can cyclic depends on this package
                CrateGraphTestmarks.cyclicDevDependency.hit()
                cyclic += dep
                null
            }
        }
        return LoweredAndCyclicDependencies(lowered, cyclic)
    }

    private fun lowerNonLibraryCratesLater(ctx: NonLibraryCrates) {
        if (ctx.cyclicDevDependencies.isEmpty()) {
            ctx.lowerNonLibraryCrates()
        } else {
            cratesToLowerLater += ctx
        }
    }

    private class NonLibraryCrates(
        val pkg: ProjectPackage,
        val doneState: NodeState.Done,
        val normalAndNonCyclicTestDeps: List<Crate.Dependency>,
        val cyclicDevDependencies: List<CargoWorkspace.Dependency>,
        val flatNormalAndNonCyclicDevDeps: LinkedHashSet<Crate>
    )

    private fun NonLibraryCrates.lowerNonLibraryCrates() {
        val cyclicDevDeps = lowerDependencies(cyclicDevDependencies, pkg)
        val normalAndTestDeps = normalAndNonCyclicTestDeps + cyclicDevDeps

        val libCrate = doneState.libCrate
        val (depsWithLib, flatDepsWithLib) = if (libCrate != null) {
            val libDep = Crate.Dependency(libCrate.normName, libCrate)

            val flatDeps = LinkedHashSet<Crate>(flatNormalAndNonCyclicDevDeps)
            flatDeps += cyclicDevDeps.flattenTopSortedDeps()
            flatDeps += libCrate

            Pair(normalAndTestDeps + libDep, flatDeps)
        } else {
            normalAndTestDeps to flatNormalAndNonCyclicDevDeps
        }

        val nonLibraryCrates = pkg.pkg.targets.mapNotNull { target ->
            if (target.kind.isLib || target.kind == CargoWorkspace.TargetKind.CustomBuild) return@mapNotNull null

            CargoBasedCrate(pkg.project, target, depsWithLib, flatDepsWithLib)
        }

        doneState.nonLibraryCrates += nonLibraryCrates
        topSortedCrates += nonLibraryCrates
        if (cyclicDevDeps.isNotEmpty()) {
            libCrate?.cyclicDevDeps = cyclicDevDeps
        }
    }

    fun build(): CrateGraph {
        for (ctx in cratesToLowerLater) {
            ctx.lowerNonLibraryCrates()
        }
        for (ctx in cratesToReplaceTargetLater) {
            ctx.replaceProjectAndTarget()
        }

        topSortedCrates.assertTopSorted()

        val idToCrate = TIntObjectHashMap<Crate>()
        for (crate in topSortedCrates) {
            crate.checkInvariants()

            val id = crate.id
            if (id != null) {
                idToCrate.put(id, crate)
            }
        }
        return CrateGraph(topSortedCrates, idToCrate)
    }
}

private fun Crate.checkInvariants() {
    if (!isUnitTestMode) return

    flatDependencies.assertTopSorted()

    for (dep in dependencies) {
        check(dep.crate in flatDependencies) {
            "Error in structure of crate `$this`: no `${dep.crate}`" +
                " dependency in flatDependencies: $flatDependencies"
        }
    }
}

private fun Iterable<Crate>.assertTopSorted() {
    if (!isUnitTestMode) return
    val set = hashSetOf<Crate>()
    for (crate in this) {
        check(crate.dependencies.all { it.crate in set })
        set += crate
    }
}

private fun mergeFeatures(
    features1: Map<String, FeatureState>,
    features2: Map<String, FeatureState>
): Map<String, FeatureState> {
    val featureMap = features1.toMutableMap()
    for ((k, v) in features2) {
        featureMap.merge(k, v) { v1, v2 ->
            when {
                v1 == FeatureState.Enabled -> FeatureState.Enabled
                v2 == FeatureState.Enabled -> FeatureState.Enabled
                else -> FeatureState.Disabled
            }
        }
    }
    return featureMap
}

private data class ProjectPackage(
    val project: CargoProject,
    val pkg: CargoWorkspace.Package,
    // Extracted to a field due to performance reasons
    val rootDirectory: Path = pkg.rootDirectory
)

private sealed class NodeState {
    data class Done(
        val libCrate: CargoBasedCrate?,
        val nonLibraryCrates: MutableList<CargoBasedCrate> = mutableListOf(),
        val pkgs: MutableSet<CargoWorkspace.Package> = hashSetOf()
    ) : NodeState()

    object Processing : NodeState()
}

private class CyclicGraphException(crateName: String) : RuntimeException("Cyclic graph detected") {
    private val stack: MutableList<String> = mutableListOf(crateName)

    fun pushCrate(crateName: String) {
        stack += crateName
    }

    override val message: String?
        get() = super.message + stack.asReversed().joinToString(prefix = " (", separator = " -> ", postfix = ")")
}

