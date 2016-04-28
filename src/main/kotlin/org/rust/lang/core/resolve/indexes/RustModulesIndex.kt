package org.rust.lang.core.resolve.indexes

import com.intellij.openapi.module.Module
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.util.indexing.FileBasedIndex
import com.intellij.util.indexing.ID
import org.rust.lang.core.names.RustQualifiedName
import org.rust.lang.core.psi.RustMod
import org.rust.lang.core.psi.impl.RustFile
import org.rust.lang.core.psi.impl.modulePath
import org.rust.lang.core.psi.util.module
import org.rust.lang.core.resolve.RustResolveEngine

interface RustModulesIndex {

    companion object {

        val ID: ID<RustModulePath, RustQualifiedName> =
            com.intellij.util.indexing.ID.create("org.rust.lang.indexes.RustModulesIndex")

        fun getSuperFor(mod: RustFile): RustMod? =
            mod.containingFile.originalFile.let { file ->
                mod.module?.let { module ->
                    file.modulePath?.let { path ->
                        findByHeterogeneous(
                            FileBasedIndex.getInstance()
                                .getValues(ID, path, GlobalSearchScope.allScope(module.project))
                                .firstOrNull(),
                            module
                        )
                    }
                }
            }

        private fun findByHeterogeneous(path: RustQualifiedName?, module: Module): RustMod? =
            path?.let {
                RustResolveEngine.resolve(path, module).resolved as RustMod?
            }
    }
}
