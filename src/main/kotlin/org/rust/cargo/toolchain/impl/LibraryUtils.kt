package org.rust.cargo.toolchain.impl

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.roots.libraries.Library
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar
import com.intellij.openapi.vfs.VirtualFile

val Module.cargoLibraryName: String get() = "Cargo <$name>"

val Module.rustLibraryName: String get() = "Rust <$name>"

fun updateLibrary(module: Module, libraryName: String, roots: Collection<VirtualFile>) {
    check(ApplicationManager.getApplication().isWriteAccessAllowed)

    val libraryTable = LibraryTablesRegistrar.getInstance().getLibraryTable(module.project)
    val library = libraryTable.getLibraryByName(libraryName)
        ?: libraryTable.createLibrary(libraryName)
        ?: return

    fillLibrary(library, roots)

    ModuleRootModificationUtil.addDependency(module, library)
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

