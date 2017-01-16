package org.rust.lang.core.stubs.index

import com.intellij.openapi.util.io.FileUtil
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.stubs.IndexSink
import com.intellij.psi.stubs.StringStubIndexExtension
import com.intellij.psi.stubs.StubIndex
import com.intellij.psi.stubs.StubIndexKey
import com.intellij.util.PathUtil
import org.rust.lang.core.psi.RsMod
import org.rust.lang.core.psi.RsModDeclItem
import org.rust.lang.core.psi.containingMod
import org.rust.lang.core.psi.impl.RsFile
import org.rust.lang.core.psi.impl.mixin.pathAttribute
import org.rust.lang.core.stubs.RsFileStub
import org.rust.lang.core.stubs.RsModDeclItemStub

class RsModulesIndex : StringStubIndexExtension<RsModDeclItem>() {
    override fun getVersion(): Int = RsFileStub.Type.stubVersion
    override fun getKey(): StubIndexKey<String, RsModDeclItem> = KEY

    companion object {
        fun getSuperFor(mod: RsFile): RsMod? {
            val key = key(mod) ?: return null
            val project = mod.project

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


        fun index(stub: RsModDeclItemStub, indexSink: IndexSink) {
            val key = key(stub.psi)
            if (key != null) {
                indexSink.occurrence(KEY, key)
            }
        }

        private val KEY: StubIndexKey<String, RsModDeclItem> =
            StubIndexKey.createIndexKey("org.rust.lang.core.stubs.index.RustModulesIndex")

        // We use case-insensitive name as a key, because certain file systems
        // are case-insensitive. It will work correctly with case-sensitive fs
        // because we of the resolv check we do in [getSuperFor]
        private fun key(mod: RsFile): String? = mod.modName?.toLowerCase()

        private fun key(mod: RsModDeclItem): String? {
            val pathAttribute = mod.pathAttribute
            return if (pathAttribute != null) {
                FileUtil.getNameWithoutExtension(PathUtil.getFileName(pathAttribute))
            } else {
                mod.name
            }?.toLowerCase()
        }
    }
}
