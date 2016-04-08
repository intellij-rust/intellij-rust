package org.rust.cargo

import com.intellij.openapi.vfs.*
import com.intellij.util.containers.BidirectionalMap
import com.intellij.util.containers.MultiMap


/**
 * Rust project model represented roughly in the same way as in Cargo itself.
 *
 * [org.rust.cargo.toolchain.CargoMetadataService] is responsible for providing a [CargoProjectDescription] for
 * an IDEA module.
 */
class CargoProjectDescription private constructor(
    private val idToPackage: BidirectionalMap<Int, Package>,
    private val dependencies: MultiMap<Int, Int>
) {
    val packages: Collection<Package> get() = idToPackage.values
    val rawDependencies: MultiMap<Int, Int> get() = MultiMap(dependencies)

    data class Package(
        val contentRootUrl: String,
        val name: String,
        val version: String,
        val targets: Collection<Target>,
        val source: String?
    ) {
        val isModule: Boolean get() = source == null
        val libTarget: Target? get() = targets.find { it.isLib }

        val virtualFile: VirtualFile? get() = VirtualFileManager.getInstance().findFileByUrl(contentRootUrl)
    }

    data class Target(
        /**
         * Absolute path to the crate root file
         */
        val url: String,
        val kind: TargetKind
    ) {
        val isLib: Boolean get() = kind == TargetKind.LIB

        val virtualFile: VirtualFile? get() = VirtualFileManager.getInstance().findFileByUrl(url)
    }

    enum class TargetKind {
        LIB, BIN, TEST, EXAMPLE, BENCH, UNKNOWN
    }

    companion object {
        fun create(packages: Collection<Package>, dependencies: MultiMap<Int, Int>): CargoProjectDescription? {
            val idToPackage: BidirectionalMap<Int, Package> = BidirectionalMap<Int, Package>().apply {
                putAll(packages.mapIndexed { i, p -> i to p })
            }
            val deps: MultiMap<Int, Int> = dependencies.copy()
            if (isValid(idToPackage, deps)) {
                return CargoProjectDescription(idToPackage, deps)
            }
            return null

        }

        private fun isValid(idToPackage: BidirectionalMap<Int, Package>, dependencies: MultiMap<Int, Int>): Boolean {
            // Has root package
            if (!idToPackage.containsKey(0)) return false

            // all ids in `dependencies` map are known
            for ((pkg, deps) in dependencies.entrySet()) {
                if (!idToPackage.containsKey(pkg)) return false
                for (dep in deps) {
                    if (!idToPackage.containsKey(dep)) return false
                }
            }

            // No need to check if every package from `idToPackage` has
            // dependencies specifier, because `MultyMap` returns empty collection
            // as a default

            //TODO: check for cycles?
            return true
        }
    }
}

