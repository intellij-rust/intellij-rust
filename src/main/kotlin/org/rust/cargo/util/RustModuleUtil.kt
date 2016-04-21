package org.rust.cargo.util

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.roots.libraries.Library
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import org.rust.cargo.project.CargoProjectDescription
import org.rust.cargo.project.CargoProjectDescriptionData

object RustModuleUtil


/**
 * Established Cargo's library name
 */
val Module.cargoLibraryName: String get() = "Cargo <$name>"

/**
 * Established Rust's 'stdlib' library name
 */
val Module.rustLibraryName: String get() = "Rust <$name>"

/**
 * Extracts content- and library-(ordered)-entries for the given module
 */
fun Module.getSourceAndLibraryRoots(): Collection<VirtualFile> =
    ModuleRootManager.getInstance(this).orderEntries.flatMap {
        it.getFiles(OrderRootType.CLASSES).toList() +
            it.getFiles(OrderRootType.SOURCES).toList()
    }

/**
 * Makes given path relative to the content-root of the module or
 * one of the respective's dependencies
 */
fun Module.relativise(f: VirtualFile): String? =
    getSourceAndLibraryRoots()
        .find {
            FileUtil.isAncestor(it.path, f.path, /* strict = */ false)
        }
        ?.let {
            FileUtil.getRelativePath(it.canonicalPath!!, f.canonicalPath!!, '/')
        }

/**
 * Creates an IDEA library from an archive with rust. Returns Packages describing crates from the standard library.
 * Hopefully this function will go away when a standard way to get rust sources appears.
 */
fun Module.attachStandardLibrary(sourcesArchive: VirtualFile): Collection<CargoProjectDescriptionData.Package> {
    val baseDir = sourcesArchive.children.singleOrNull()?.findChild("src") ?: return emptyList()
    val packages = listOf("std", "core", "collections").mapNotNull { standardLibraryPackage(baseDir, it) }
    val fs = VirtualFileManager.getInstance()
    val libraryRoots = packages
        .flatMap { pkg -> pkg.targets.mapNotNull { it.url } }
        .mapNotNull { fs.findFileByUrl(it)?.parent }

    updateLibrary(rustLibraryName, libraryRoots)
    return packages
}

private fun standardLibraryPackage(baseDir: VirtualFile, name: String): CargoProjectDescriptionData.Package? {
    return CargoProjectDescriptionData.Package(
        baseDir.findFileByRelativePath("lib$name")?.url ?: return null,
        name,
        version = "1.0.0",
        targets = listOf(CargoProjectDescriptionData.Target(
            baseDir.findFileByRelativePath("lib$name/lib.rs")?.url ?: return null,
            CargoProjectDescription.TargetKind.LIB
        )),
        source = "stdlib"
    )
}

/**
 * Updates `CLASS` order-entries for the supplied module's library (referred to with `libraryName`)
 */
fun Module.updateLibrary(libraryName: String, roots: Collection<VirtualFile>) {
    check(ApplicationManager.getApplication().isWriteAccessAllowed)

    val libraryTable = LibraryTablesRegistrar.getInstance().getLibraryTable(project)
    val library = libraryTable.getLibraryByName(libraryName)
        ?: libraryTable.createLibrary(libraryName)
        ?: return

    fillLibrary(library, roots)

    ModuleRootModificationUtil.addDependency(this, library)
}

private fun fillLibrary(library: Library, roots: Collection<VirtualFile>) {
    val model = library.modifiableModel
    for (url in library.getUrls(OrderRootType.CLASSES)) {
        model.removeRoot(url, OrderRootType.CLASSES)
    }

    for (root in roots) {
        model.addRoot(root, OrderRootType.CLASSES)
    }

    model.commit()
}

