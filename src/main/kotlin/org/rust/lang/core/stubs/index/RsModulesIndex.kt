/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.stubs.index

import com.intellij.openapi.util.io.FileUtil
import com.intellij.psi.stubs.IndexSink
import com.intellij.psi.stubs.StringStubIndexExtension
import com.intellij.psi.stubs.StubIndex
import com.intellij.psi.stubs.StubIndexKey
import com.intellij.util.PathUtil
import com.intellij.util.SmartList
import org.rust.ide.search.RsWithMacrosProjectScope
import org.rust.lang.RsConstants
import org.rust.lang.core.psi.RsFile
import org.rust.lang.core.psi.RsModDeclItem
import org.rust.lang.core.psi.ext.RsAttr
import org.rust.lang.core.psi.ext.getTraversedRawAttributes
import org.rust.lang.core.psi.isValidProjectMember
import org.rust.lang.core.stubs.RsFileStub
import org.rust.lang.core.stubs.RsModDeclItemStub
import org.rust.openapiext.checkCommitIsNotInProgress
import org.rust.stdext.singleOrFilter

class RsModulesIndex : StringStubIndexExtension<RsModDeclItem>() {
    override fun getVersion(): Int = RsFileStub.Type.stubVersion
    override fun getKey(): StubIndexKey<String, RsModDeclItem> = KEY

    companion object {
        fun getDeclarationsFor(mod: RsFile): List<RsModDeclItem> {
            val key = key(mod) ?: return emptyList()
            val project = mod.project
            checkCommitIsNotInProgress(project)

            val result = SmartList<RsModDeclItem>()

            val scope = RsWithMacrosProjectScope(project)

            fun processElements(key: String) {
                StubIndex.getInstance().processElements(
                    KEY, key, project, scope, RsModDeclItem::class.java
                ) { modDecl ->
                    if (modDecl.reference.resolve() == mod) {
                        result += modDecl
                    }
                    true
                }
            }
            processElements(key)
            if (mod.name == RsConstants.MOD_RS_FILE && result.isEmpty()) {
                // Parent path will be empty for #[path = "mod.rs"]
                processElements("")
            }

            return result.singleOrFilter { it.isValidProjectMember }.takeIf { it.isNotEmpty() } ?: result
        }

        fun index(stub: RsModDeclItemStub, indexSink: IndexSink) {
            for (key in keys(stub.psi)) {
                indexSink.occurrence(KEY, key)
            }
        }

        private val KEY: StubIndexKey<String, RsModDeclItem> =
            StubIndexKey.createIndexKey("org.rust.lang.core.stubs.index.RustModulesIndex")

        // We use case-insensitive name as a key, because certain file systems
        // are case-insensitive. It will work correctly with case-sensitive fs
        // because of the resolve check we do in [getDeclarationFor]
        private fun key(mod: RsFile): String? {
            val name = if (mod.name != RsConstants.MOD_RS_FILE) FileUtil.getNameWithoutExtension(mod.name) else mod.parent?.name
            return name?.toLowerCase()
        }

        private fun keys(mod: RsModDeclItem): Sequence<String> {
            val pathAttributes = mod.getTraversedRawAttributes()
                .attrsByName("path")
                .toList()
            return if (pathAttributes.isNotEmpty()) {
                val isUnderCfgAttr = pathAttributes.any { it.parent !is RsAttr }
                val attrKeys = pathAttributes.asSequence().mapNotNull { pathAttribute ->
                    val pathAttributeValue = pathAttribute.value ?: return@mapNotNull null
                    val fileName = PathUtil.getFileName(pathAttributeValue)
                    if (fileName == RsConstants.MOD_RS_FILE) {
                        // Use the name of the parent directory for files named mod.rs
                        // Will be empty string for #[path = "mod.rs"]
                        PathUtil.getFileName(PathUtil.getParentPath(pathAttributeValue))
                    } else {
                        FileUtil.getNameWithoutExtension(fileName)
                    }
                }
                if (isUnderCfgAttr) {
                    sequenceOf(mod.name) + attrKeys
                } else {
                    attrKeys
                }
            } else {
                sequenceOf(mod.name)
            }.mapNotNull { it?.toLowerCase() }
        }
    }
}
