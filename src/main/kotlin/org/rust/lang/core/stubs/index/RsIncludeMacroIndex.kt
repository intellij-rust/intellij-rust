/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.stubs.index

import com.intellij.openapi.util.Computable
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.stubs.IndexSink
import com.intellij.psi.stubs.StringStubIndexExtension
import com.intellij.psi.stubs.StubIndex
import com.intellij.psi.stubs.StubIndexKey
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.util.PathUtil
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.*
import org.rust.lang.core.stubs.RsFileStub
import org.rust.lang.core.stubs.RsMacroCallStub
import org.rust.openapiext.recursionGuard

class RsIncludeMacroIndex : StringStubIndexExtension<RsMacroCall>() {
    override fun getVersion(): Int = RsFileStub.Type.stubVersion
    override fun getKey(): StubIndexKey<String, RsMacroCall> = KEY

    companion object {
        val KEY : StubIndexKey<String, RsMacroCall> =
            StubIndexKey.createIndexKey("org.rust.lang.core.stubs.index.RsIncludeMacroIndex")

        fun index(stub: RsMacroCallStub, indexSink: IndexSink) {
            val key = key(stub.psi) ?: return
            indexSink.occurrence(KEY, key)
        }

        /**
         * Returns mod item that includes given [file] via `include!()` macro
         */
        fun getIncludingMod(file: RsFile): RsMod? {
            val originalFile = file.originalFile as? RsFile ?: return null
            return CachedValuesManager.getCachedValue(originalFile) {
                CachedValueProvider.Result.create(
                    getIncludingModInternal(originalFile),
                    originalFile.rustStructureOrAnyPsiModificationTracker
                )
            }
        }

        private fun getIncludingModInternal(file: RsFile): RsMod? {
            return recursionGuard(file, Computable {
                val key = file.name
                val project = file.project

                var parentMod: RsMod? = null
                StubIndex.getInstance().processElements(KEY, key, project, GlobalSearchScope.allScope(project), RsMacroCall::class.java) { macroCall ->
                    val includingFile = macroCall.findIncludingFile()
                    if (includingFile == file) {
                        parentMod = macroCall.containingMod
                        false
                    } else {
                        true
                    }
                }
                parentMod
            })
        }

        private fun key(call: RsMacroCall): String? {
            return call.includeMacroArgument?.expr?.includingFileName()
        }

        private fun RsExpr.includingFileName(): String? {
            return when (this) {
                is RsLitExpr -> {
                    val path = stringValue ?: return null
                    PathUtil.getFileName(path)
                }
                is RsMacroExpr -> {
                    val macroCall = macroCall
                    if (macroCall.macroName == "concat") {
                        // We need only last segment of path because we use file name as index key
                        macroCall.concatMacroArgument?.exprList?.lastOrNull()?.includingFileName()
                    } else {
                        null
                    }
                }
                else -> null
            }
        }
    }
}
