/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.stubs.index

import com.intellij.openapi.util.io.FileUtil
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.stubs.IndexSink
import com.intellij.psi.stubs.StringStubIndexExtension
import com.intellij.psi.stubs.StubIndex
import com.intellij.psi.stubs.StubIndexKey
import com.intellij.util.PathUtil
import org.rust.ide.RsConstants
import org.rust.lang.core.psi.RsModDeclItem
import org.rust.lang.core.psi.RsFile
import org.rust.lang.core.psi.ext.pathAttribute
import org.rust.lang.core.stubs.RsFileStub
import org.rust.lang.core.stubs.RsModDeclItemStub

class RsModulesIndex : StringStubIndexExtension<RsModDeclItem>() {
    override fun getVersion(): Int = RsFileStub.Type.stubVersion
    override fun getKey(): StubIndexKey<String, RsModDeclItem> = KEY

    companion object {
        fun getDeclarationFor(mod: RsFile): RsModDeclItem? {
            val key = key(mod) ?: return null
            val project = mod.project

            var result: RsModDeclItem? = null

            StubIndex.getInstance().processElements(
                KEY, key, project, GlobalSearchScope.allScope(project), RsModDeclItem::class.java
            ) { modDecl ->
                if (modDecl.reference.resolve() == mod) {
                    result = modDecl
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
        // because we of the resolve check we do in [getDeclarationFor]
        private fun key(mod: RsFile): String? = mod.modName?.toLowerCase()

        private fun key(mod: RsModDeclItem): String? {
            val pathAttribute = mod.pathAttribute
            return if (pathAttribute != null) {
                val fileName = PathUtil.getFileName(pathAttribute)
                if (fileName == RsConstants.MOD_RS_FILE)
                    // Use the name of the parent directory for files named mod.rs
                    PathUtil.getFileName(PathUtil.getParentPath(pathAttribute))
                else
                    FileUtil.getNameWithoutExtension(fileName)
            } else {
                mod.name
            }?.toLowerCase()
        }
    }
}
