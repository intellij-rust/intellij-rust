package org.rust.lang.core.modules

import com.intellij.openapi.module.Module
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.indexing.FileBasedIndex
import com.intellij.util.indexing.ID
import org.rust.cargo.project.module.util.rootMod
import org.rust.lang.core.names.RustQualifiedName
import org.rust.lang.core.psi.RustModItem
import org.rust.lang.core.psi.util.getCrate
import org.rust.lang.core.resolve.RustResolveEngine

interface RustModulesIndex {

    companion object {

        val indexID: ID<VirtualFile, RustQualifiedName> = ID.create("org.rust.lang.core.modules.RustModulesIndex")

        fun getInstance(m: Module): RustModulesIndex? =
            m.getComponent(RustModulesIndex::class.java)


        fun getSuperFor(mod: RustModItem): RustModItem? =
            mod.containingFile.virtualFile?.let {
                val crate = mod.getCrate()
                findByCanonical(
                    FileBasedIndex.getInstance().getValues(indexID, it, crate.moduleContentScope)
                                                .single(),
                    crate
                )
            }

        private fun findByCanonical(name: RustQualifiedName?, crate: Module): RustModItem? =
            name?.let {
                RustResolveEngine.resolve(name, crate).resolved as RustModItem?
            } ?: crate.rootMod
    }
}
