/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.crate

import com.intellij.openapi.vfs.VirtualFile
import org.rust.cargo.CfgOptions
import org.rust.cargo.project.model.CargoProject
import org.rust.cargo.project.workspace.CargoWorkspace
import org.rust.cargo.project.workspace.FeatureState
import org.rust.cargo.project.workspace.PackageOrigin
import org.rust.lang.core.psi.RsFile
import java.util.*

/**
 * An immutable object describes a *crate* from the *rustc* point of view.
 * In Cargo-based project this is usually a wrapper around [CargoWorkspace.Target]
 */
interface Crate {
    /**
     * This id can be saved to a disk and then used to find the crate via [CrateGraphService.findCrateById].
     * Can be `null` for crates that are not represented in the physical filesystem and can't be retrieved
     * using [CrateGraphService.findCrateById], or for invalid crates (without a root module)
     */
    val id: CratePersistentId?
    val edition: CargoWorkspace.Edition

    val cargoProject: CargoProject
    val cargoWorkspace: CargoWorkspace
    val cargoTarget: CargoWorkspace.Target?
    val kind: CargoWorkspace.TargetKind
    val origin: PackageOrigin

    val cfgOptions: CfgOptions
    val features: Map<String, FeatureState>

    /** A map of compile-time environment variables, needed for `env!("FOO")` macros expansion */
    val env: Map<String, String>

    /** Represents `OUT_DIR` compile-time environment variable. Used for `env!("OUT_DIR")` macros expansion */
    val outDir: VirtualFile?

    /** Direct dependencies */
    val dependencies: Collection<Dependency>

    /** All dependencies (including transitive) of this crate. Topological sorted */
    val flatDependencies: LinkedHashSet<Crate>

    /** Other crates that depends on this crate */
    val reverseDependencies: List<Crate>

    /**
     * A cargo package can have cyclic dependencies through `[dev-dependencies]` (see [CrateGraphService] docs).
     * Cyclic dependencies are not contained in [dependencies], [flatDependencies] or [reverseDependencies].
     */
    @JvmDefault
    val dependenciesWithCyclic: Collection<Dependency>
        get() = dependencies

    /**
     * A root module of the crate, also known as "crate root". Usually it's `main.rs` or `lib.rs`.
     * Use carefully: can be null or invalid ([VirtualFile.isValid])
     */
    val rootModFile: VirtualFile?
    val rootMod: RsFile?

    val areDoctestsEnabled: Boolean

    /** A name to display to a user */
    val presentableName: String

    /**
     * A name that can be used as a valid Rust identifier. Usually it is [presentableName] with "-" chars
     * replaced to "_".
     *
     * NOTE that Rust crate doesn't have any kind of "global" name. The actual crate name can be different
     * in a particular dependent crate. Use [Dependency.normName] instead
     */
    val normName: String

    data class Dependency(
        /** A name of the dependency that can be used in `extern crate name;` or in absolute paths */
        val normName: String,

        val crate: Crate
    )
}

fun Crate.findDependency(normName: String): Crate? =
    dependencies.find { it.normName == normName }?.crate

fun Crate.hasDirectDependency(other: Crate): Boolean =
    dependencies.any { it.crate == other }
