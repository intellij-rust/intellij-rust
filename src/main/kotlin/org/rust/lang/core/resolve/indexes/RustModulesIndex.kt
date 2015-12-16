package org.rust.lang.core.resolve.indexes

import com.intellij.openapi.module.Module
import com.intellij.util.indexing.FileBasedIndex
import com.intellij.util.indexing.ID
import org.rust.lang.core.names.RustQualifiedName
import org.rust.lang.core.psi.RustModItem
import org.rust.lang.core.psi.util.getCrate
import org.rust.lang.core.resolve.RustResolveEngine

interface RustModulesIndex {

    companion object {

        internal val ID: ID<RustModulePath, RustQualifiedName> =
            com.intellij.util.indexing.ID.create("org.rust.lang.indexes.RustModulesIndex")

        fun getSuperFor(mod: RustModItem): RustModItem? =
            mod.containingFile.let {
                val crate = mod.getCrate()
                findByCanonical(
                    FileBasedIndex.getInstance().getValues(ID, RustModulePath.devise(it), crate.moduleContentScope)
                                                .firstOrNull(),
                    crate
                )
            }

        private fun findByCanonical(name: RustQualifiedName?, crate: Module): RustModItem? =
            name?.let {
                RustResolveEngine.resolve(name, crate).resolved as RustModItem?
            }
    }
}
