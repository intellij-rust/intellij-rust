package org.rust.cargo.toolchain

import com.intellij.openapi.module.Module
import com.intellij.openapi.vfs.VirtualFile
import org.rust.cargo.CargoProjectDescription
import org.rust.cargo.toolchain.impl.rustLibraryName
import org.rust.cargo.toolchain.impl.updateLibrary


/*
 * Uses `cargo metadata` command to update IDEA libraries and Cargo project model.
 */
interface CargoMetadataService {
    /**
     * Updates Rust libraries asynchronously. Consecutive updates are coalesced.
     */
    fun scheduleUpdate(toolchain: RustToolchain)

    /**
     * Immediately schedules an update. Shows balloon upon completion.
     *
     * Update is still asynchronous.
     */
    fun updateNow(toolchain: RustToolchain)
    val cargoProject: CargoProjectDescription?
}

/*
 * Creates an IDEA library from an archive with rust. Returns Packages describing crates from the standard library.
 * Hopefully this function will go away when a standard way to get rust sources appears.
 */
fun attachStandardLibrary(module: Module, sourcesArchive: VirtualFile): Collection<CargoProjectDescription.Package> {
    val baseDir = sourcesArchive.children.singleOrNull()?.findChild("src") ?: return emptyList()
    val packages = listOf("std", "core", "collections").mapNotNull { standardLibrayPackage(baseDir, it) }
    updateLibrary(module, module.rustLibraryName, packages.mapNotNull { it.virtualFile })
    return packages
}

private fun standardLibrayPackage(baseDir: VirtualFile, name: String): CargoProjectDescription.Package? {
    return CargoProjectDescription.Package(
        baseDir.findFileByRelativePath("lib$name")?.url ?: return null,
        name,
        version = "1.0.0",
        targets = listOf(CargoProjectDescription.Target(
            baseDir.findFileByRelativePath("lib$name/lib.rs")?.url ?: return null,
            CargoProjectDescription.TargetKind.LIB
        )),
        source = "stdlib"
    )
}
