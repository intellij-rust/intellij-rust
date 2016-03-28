package org.rust.lang.core.resolve.indexes

import com.intellij.openapi.module.Module
import com.intellij.util.indexing.FileBasedIndex
import com.intellij.util.indexing.ID
import org.rust.lang.core.names.RustQualifiedName
import org.rust.lang.core.psi.RustModItem
import org.rust.lang.core.psi.impl.modulePath
import org.rust.lang.core.psi.util.getModule
import org.rust.lang.core.resolve.RustResolveEngine

interface RustModulesIndex {

    companion object {

        internal val ID: ID<RustModulePath, RustQualifiedName> =
            com.intellij.util.indexing.ID.create("org.rust.lang.indexes.RustModulesIndex")

        fun getSuperFor(mod: RustModItem): RustModItem? =
            mod.containingFile.originalFile.let { file ->
                mod.getModule()?.let { module ->
                    file.modulePath?.let { path ->
                        findByHeterogeneous(
                            FileBasedIndex.getInstance()
                                .getValues(ID, path, module.moduleContentScope)
                                .firstOrNull(),
                            module
                        )
                    }
                }
            }

        private fun findByHeterogeneous(path: RustQualifiedName?, module: Module): RustModItem? =
            path?.let {
                RustResolveEngine.resolve(path, module).resolved as RustModItem?
            }
    }
}
