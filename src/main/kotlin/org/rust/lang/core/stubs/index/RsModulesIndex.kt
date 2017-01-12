package org.rust.lang.core.stubs.index

import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.stubs.StringStubIndexExtension
import com.intellij.psi.stubs.StubIndex
import com.intellij.psi.stubs.StubIndexKey
import org.rust.lang.core.psi.RsModDeclItem
import org.rust.lang.core.psi.RsMod
import org.rust.lang.core.psi.containingMod
import org.rust.lang.core.psi.impl.RsFile
import org.rust.lang.core.stubs.RsFileStub

class RsModulesIndex : StringStubIndexExtension<RsModDeclItem>() {
    override fun getVersion(): Int = RsFileStub.Type.stubVersion
    override fun getKey(): StubIndexKey<String, RsModDeclItem> = KEY

    companion object {
        val KEY: StubIndexKey<String, RsModDeclItem> =
            StubIndexKey.createIndexKey("org.rust.lang.core.stubs.index.RustModulesIndex")

        fun getSuperFor(mod: RsFile): RsMod? {
            val project = mod.project
            val key = mod.modName ?: return null

            var result: RsMod? = null

            StubIndex.getInstance().processElements(
                KEY, key, project, GlobalSearchScope.allScope(project), RsModDeclItem::class.java
            ) { modDecl ->
                if (modDecl.reference.resolve() == mod) {
                    result = modDecl.containingMod
                    false
                } else {
                    true
                }
            }

            return result
        }
    }
}
