/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.stubs.index

import com.intellij.openapi.util.Computable
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.stubs.*
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.util.PathUtil
import com.intellij.util.io.KeyDescriptor
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.*
import org.rust.lang.core.stubs.RsFileStub
import org.rust.lang.core.stubs.RsMacroCallStub
import org.rust.openapiext.recursionGuard
import java.io.DataInput
import java.io.DataOutput

class RsIncludeMacroIndex : AbstractStubIndex<IncludeMacroKey, RsMacroCall>() {
    override fun getVersion(): Int = RsFileStub.Type.stubVersion
    override fun getKey(): StubIndexKey<IncludeMacroKey, RsMacroCall> = KEY
    override fun getKeyDescriptor(): KeyDescriptor<IncludeMacroKey> = IncludeMacroKey.KeyDescriptor

    companion object {
        val KEY : StubIndexKey<IncludeMacroKey, RsMacroCall> =
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
            return makeIndexLookup(IncludeMacroKey(file.name), file)
                ?: makeIndexLookup(IncludeMacroKey.UNKNOWN_FILE_NAME, file)
        }

        private fun makeIndexLookup(key: IncludeMacroKey, file: RsFile): RsMod? {
            return recursionGuard(file, Computable {
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

        private fun key(call: RsMacroCall): IncludeMacroKey? {
            return call.includeMacroArgument?.expr?.includingFileName()
        }

        private fun RsExpr.includingFileName(): IncludeMacroKey? {
            return when (this) {
                is RsLitExpr -> {
                    val path = stringValue ?: return null
                    IncludeMacroKey(PathUtil.getFileName(path))
                }
                is RsMacroExpr -> {
                    val macroCall = macroCall
                    if (macroCall.macroName == "concat") {
                        // We need only last segment of path because we use file name as index key
                        macroCall.concatMacroArgument?.exprList?.lastOrNull()?.includingFileName()
                    } else {
                        IncludeMacroKey.UNKNOWN_FILE_NAME
                    }
                }
                else -> IncludeMacroKey.UNKNOWN_FILE_NAME
            }
        }
    }
}

data class IncludeMacroKey(val name: String) {

    object KeyDescriptor : com.intellij.util.io.KeyDescriptor<IncludeMacroKey> {
        override fun save(out: DataOutput, value: IncludeMacroKey) =
            out.writeUTF(value.name)

        override fun read(`in`: DataInput): IncludeMacroKey =
            IncludeMacroKey(`in`.readUTF())

        override fun getHashCode(value: IncludeMacroKey): Int = value.hashCode()
        override fun isEqual(lhs: IncludeMacroKey, rhs: IncludeMacroKey): Boolean = lhs == rhs
    }

    companion object {
        val UNKNOWN_FILE_NAME: IncludeMacroKey = IncludeMacroKey("*")
    }
}
