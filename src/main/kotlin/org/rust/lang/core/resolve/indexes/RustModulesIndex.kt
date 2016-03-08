package org.rust.lang.core.resolve.indexes

import com.intellij.psi.PsiManager
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.util.indexing.FileBasedIndex
import com.intellij.util.indexing.ID
import org.rust.lang.core.psi.RustModItem
import org.rust.lang.core.psi.impl.rustMod

object RustModulesIndex {
    val ID: ID<VirtualFileUrl, VirtualFileUrl> =
        com.intellij.util.indexing.ID.create("org.rust.lang.indexes.RustModulesIndex")

    fun getSuperFor(mod: RustModItem): RustModItem? =
        mod.containingFile.virtualFile?.let { file ->
            val url = VirtualFileUrl(file)
            val superUrl = FileBasedIndex.getInstance()
                .getValues(ID, url, GlobalSearchScope.allScope(mod.project))
                .firstOrNull()

            superUrl?.resolve()?.let {
                PsiManager.getInstance(mod.project).findFile(it)?.rustMod
            }
        }

}
