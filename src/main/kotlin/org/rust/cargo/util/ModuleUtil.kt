package org.rust.cargo.util

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleServiceManager
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.roots.impl.OrderEntryUtil
import com.intellij.openapi.roots.libraries.Library
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar
import com.intellij.openapi.vfs.JarFileSystem
import com.intellij.openapi.vfs.VirtualFile
import org.rust.cargo.project.CargoProjectDescription

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
 * Helper extracting generic service for the particular module
 */
inline fun<reified T: Any> Module.getService(): T? =
    ModuleServiceManager.getService(this, T::class.java)

inline fun<reified T: Any> Module.getServiceOrThrow(): T =
    getService()!!

/**
 * Helper extracting generic component for the particular module
 */
inline fun<reified T: Any> Module.getComponent(): T? =
    this.getComponent(T::class.java)

inline fun<reified T: Any> Module.getComponentOrThrow(): T =
    getComponent()!!


/**
 * Makes given path relative to the content-root of the module or
 * one of the respective's dependencies
 */
fun Module.relativise(f: VirtualFile): Pair<String, String>? =
    cargoProject?.findPackageForFile(f)?.let {
        val (pkg, relPath) = it
        pkg.name to relPath
    }

/**
 * Creates an IDEA external library from an zip archive or a folder with rust.
 * The crates can be extracted with [standardLibraryCrates].
 * Hopefully this function will go away when a standard way to get rust sources appears.
 */
fun Module.attachStandardLibrary(sourcesArchive: VirtualFile) {
    val srcDir = findSrcDir(sourcesArchive) ?: return
    val libraryRoots = stdlibCrateNames.mapNotNull {
        srcDir.findFileByRelativePath("lib$it")
    }

    updateLibrary(rustLibraryName, libraryRoots)
}

/**
 * Combines information about project structure which we got form cargo and information
 * about standard library which is stored as an IDEA external library
 */
fun Module.extendProjectDescriptionWithStandardLibraryCrates(projectDescription: CargoProjectDescription) : CargoProjectDescription {
    val lib = LibraryTablesRegistrar.getInstance().getLibraryTable(project).getLibraryByName(rustLibraryName)
        ?: return projectDescription

    val roots: Map<String, VirtualFile> = lib.getFiles(OrderRootType.CLASSES).associateBy { it.name }
    val stdlibPackages = stdlibCrateNames.mapNotNull { name ->
        roots["lib$name"]?.let { libDir ->
            val file = libDir.findFileByRelativePath("lib.rs") ?: return@let null
            name to file
        }
    }

    return projectDescription.withAdditionalPackages(stdlibPackages)
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

    ModuleRootModificationUtil.updateModel(this) { model ->
        OrderEntryUtil.findLibraryOrderEntry(model, library)?.let { previousOrderEntry ->
            model.removeOrderEntry(previousOrderEntry)
        }

        model.addLibraryEntry(library)
    }
}

private fun findSrcDir(sources: VirtualFile): VirtualFile? {
    // sources may be either a zip archive downloaded from github,
    // or a root directory with rust sources, or its src subdirectory.
    // In any case, we want to find the src subdir
    if (sources.isDirectory) {
        return if (sources.name == "src") sources else sources.findChild("src")
    }
    val base = JarFileSystem.getInstance().getJarRootForLocalFile(sources) ?: return null
    return base.children.singleOrNull()?.findChild("src")
}

private val stdlibCrateNames = listOf("std", "core", "collections", "alloc")

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

