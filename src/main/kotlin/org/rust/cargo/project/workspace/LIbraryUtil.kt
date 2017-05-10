package org.rust.cargo.project.workspace

import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.roots.impl.OrderEntryUtil
import com.intellij.openapi.roots.libraries.Library
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import org.rust.cargo.util.AutoInjectedCrates
import org.rust.cargo.util.StdLibType
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
 * Rust standard library crates sources extracted from a zip archive or a folder with rust.
 * Hopefully this class will go away when a standard way to get rust sources appears.
 */
class StandardLibrary private constructor(
    val crates: List<StdCrate>
) {

    data class StdCrate(
        val name: String,
        val type: StdLibType,
        val crateRootUrl: String,
        val packageRootUrl: String,
        val dependencies: Collection<String>
    )

    /**
     * Creates an module level IDEA external library from [crates].
     */
    fun attachTo(module: Module) {
        module.updateLibrary(module.rustLibraryName, rootCrates.map { it.packageRootUrl })
    }

    fun sameAsLibrary(library: Library): Boolean {
        val libraryUrls = library.rootProvider.getFiles(OrderRootType.CLASSES).map { it.url }.toSet()
        val myUrls = rootCrates.map { it.packageRootUrl }.toSet()
        return myUrls == libraryUrls
    }

    private val rootCrates: List<StdCrate> = crates.filter { it.type == StdLibType.ROOT }

    companion object {
        fun fromPath(path: String): StandardLibrary? =
            LocalFileSystem.getInstance().findFileByPath(path)?.let { fromFile(it) }

        fun fromFile(sources: VirtualFile): StandardLibrary? {
            if (!sources.isDirectory) return null
            val srcDir = if (sources.name == "src") sources else sources.findChild("src")
                ?: return null

            val stdlib = AutoInjectedCrates.stdlibCrates.mapNotNull { libInfo ->
                val packageSrcDir = srcDir.findFileByRelativePath(libInfo.srcDir)
                val libFile = packageSrcDir?.findChild("lib.rs")
                if (packageSrcDir != null && libFile != null)
                    StdCrate(libInfo.name, libInfo.type, libFile.url, packageSrcDir.url, libInfo.dependencies)
                else
                    null
            }
            if (stdlib.isEmpty()) return null
            return StandardLibrary(stdlib)
        }
    }
}

/**
 * Updates `CLASS` order-entries for the supplied module's library (referred to with `libraryName`)
 */
fun Module.updateLibrary(libraryName: String, urls: Collection<String>) {
    checkWriteAccessAllowed()

    val libraryTable = LibraryTablesRegistrar.getInstance().getLibraryTable(project)
    val library = libraryTable.getLibraryByName(libraryName)
        ?: libraryTable.createLibrary(libraryName)
        ?: return

    fillLibrary(library, urls)

    ModuleRootModificationUtil.updateModel(this) { model ->
        OrderEntryUtil.findLibraryOrderEntry(model, library)?.let { previousOrderEntry ->
            model.removeOrderEntry(previousOrderEntry)
        }

        model.addLibraryEntry(library)
    }
}

private fun fillLibrary(library: Library, urls: Collection<String>) {
    val model = library.modifiableModel
    for (url in library.getUrls(OrderRootType.CLASSES)) {
        model.removeRoot(url, OrderRootType.CLASSES)
    }

    for (url in urls) {
        model.addRoot(url, OrderRootType.CLASSES)
    }

    model.commit()
}

