package org.rust.lang.core.resolve.indexes

import com.intellij.openapi.module.Module
import com.intellij.util.indexing.FileBasedIndex
import com.intellij.util.indexing.ID
import org.rust.lang.core.names.RustQualifiedName
import org.rust.lang.core.psi.RustModItem
import org.rust.lang.core.psi.util.getCrate
import org.rust.lang.core.psi.util.modulePath
import org.rust.lang.core.resolve.RustResolveEngine

interface RustModulesIndex {

    companion object {

        internal val ID: ID<RustModulePath, RustQualifiedName> =
            com.intellij.util.indexing.ID.create("org.rust.lang.indexes.RustModulesIndex")

        fun getSuperFor(mod: RustModItem): RustModItem? =
            mod.containingFile.let { file ->
                mod.getCrate()?.let { crate ->
                    file.modulePath?.let { path ->
                        findByHeterogeneous(
                            FileBasedIndex.getInstance()
                                .getValues(ID, path, crate.moduleContentScope)
                                .firstOrNull(),
                            crate
                        )
                    }
                }
            }

        private fun findByHeterogeneous(path: RustQualifiedName?, crate: Module): RustModItem? =
            path?.let {
                RustResolveEngine.resolve(path, crate).resolved as RustModItem?
            }
    }
}
