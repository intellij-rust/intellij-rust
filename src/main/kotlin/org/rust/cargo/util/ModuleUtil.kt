package org.rust.cargo.util

import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.roots.impl.OrderEntryUtil
import com.intellij.openapi.roots.libraries.Library
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar
import com.intellij.openapi.vfs.JarFileSystem
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import org.rust.cargo.project.CargoProjectDescription
import org.rust.ide.utils.checkWriteAccessAllowed

/**
 * Established Cargo's library name
 */
val Module.cargoLibraryName: String get() = "Cargo <$name>"

/**
 * Established Rust's 'stdlib' library name
 */
private val Module.rustLibraryName: String get() = "Rust <$name>"

/**
 * Rust standard library crates source roots extracted from a zip archive or a folder with rust.
 * Hopefully this class will go away when a standard way to get rust sources appears.
 */
class StandardLibraryRoots private constructor(
    private val roots: List<VirtualFile>
) {

    /**
     * Creates an module level IDEA external library from [roots].
     * The crates can be extracted with [extendProjectDescriptionWithStandardLibraryCrates].
     */
    fun attachTo(module: Module) {
        module.updateLibrary(module.rustLibraryName, roots)
    }

    companion object {
        fun fromPath(path: String): StandardLibraryRoots? =
            LocalFileSystem.getInstance().findFileByPath(path)?.let { fromFile(it) }

        fun fromFile(sources: VirtualFile): StandardLibraryRoots? {
            val srcDir = if (sources.isDirectory) {
                if (sources.name == "src") sources else sources.findChild("src")
            } else {
                JarFileSystem.getInstance().getJarRootForLocalFile(sources)
                    ?.children?.singleOrNull()
                    ?.findChild("src")
            } ?: return null

            val roots = stdlibCrateNames.mapNotNull { srcDir.findFileByRelativePath("lib$it") }
            if (roots.isEmpty()) return null
            return StandardLibraryRoots(roots)
        }
    }
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
    checkWriteAccessAllowed()

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

private val stdlibCrateNames = listOf("std", "core", "collections", "alloc", "rustc_unicode", "std_unicode")

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

