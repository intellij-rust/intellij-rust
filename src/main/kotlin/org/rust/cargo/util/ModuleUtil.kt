package org.rust.cargo.util

import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.roots.impl.OrderEntryUtil
import com.intellij.openapi.roots.libraries.Library
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import org.rust.ide.utils.checkWriteAccessAllowed

/**
 * Established Cargo's library name
 */
val Module.cargoLibraryName: String get() = "Cargo <$name>"

/**
 * Established Rust's 'stdlib' library name
 */
val Module.rustLibraryName: String get() = "Rust <$name>"

/**
 * Rust standard library crates source roots extracted from a zip archive or a folder with rust.
 * Hopefully this class will go away when a standard way to get rust sources appears.
 */
class StandardLibraryRoots private constructor(
    private val roots: List<VirtualFile>
) {

    /**
     * Creates an module level IDEA external library from [roots].
     */
    fun attachTo(module: Module) {
        module.updateLibrary(module.rustLibraryName, roots)
    }

    companion object {
        fun fromPath(path: String): StandardLibraryRoots? =
            LocalFileSystem.getInstance().findFileByPath(path)?.let { fromFile(it) }

        fun fromFile(sources: VirtualFile): StandardLibraryRoots? {
            if (!sources.isDirectory) return null
            val srcDir = if (sources.name == "src") sources else sources.findChild("src")
                ?: return null

            val roots = AutoInjectedCrates.stdlibCrateNames.mapNotNull { srcDir.findFileByRelativePath("lib$it") }
            if (roots.isEmpty()) return null
            return StandardLibraryRoots(roots)
        }
    }
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

