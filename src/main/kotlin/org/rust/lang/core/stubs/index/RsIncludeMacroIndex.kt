/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.stubs.index

import com.intellij.openapi.util.Key
import com.intellij.psi.stubs.AbstractStubIndex
import com.intellij.psi.stubs.IndexSink
import com.intellij.psi.stubs.StubIndex
import com.intellij.psi.stubs.StubIndexKey
import com.intellij.psi.util.CachedValue
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.util.PathUtil
import com.intellij.util.io.KeyDescriptor
import org.rust.ide.search.RsWithMacrosProjectScope
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.findIncludingFile
import org.rust.lang.core.psi.ext.macroName
import org.rust.lang.core.psi.ext.stringValue
import org.rust.lang.core.stubs.RsFileStub
import org.rust.lang.core.stubs.RsMacroCallStub
import org.rust.openapiext.checkCommitIsNotInProgress
import org.rust.openapiext.recursionGuard
import java.io.DataInput
import java.io.DataOutput

class RsIncludeMacroIndex : AbstractStubIndex<IncludeMacroKey, RsMacroCall>() {
    override fun getVersion(): Int = RsFileStub.Type.stubVersion
    override fun getKey(): StubIndexKey<IncludeMacroKey, RsMacroCall> = KEY
    override fun getKeyDescriptor(): KeyDescriptor<IncludeMacroKey> = IncludeMacroKey.KeyDescriptor

    companion object {
        val KEY: StubIndexKey<IncludeMacroKey, RsMacroCall> =
            StubIndexKey.createIndexKey("org.rust.lang.core.stubs.index.RsIncludeMacroIndex")

        private val INCLUDING_MOD_KEY: Key<CachedValue<RsMacroCall?>> = Key.create("INCLUDING_MOD_KEY")

        fun index(stub: RsMacroCallStub, indexSink: IndexSink) {
            val key = key(stub.psi) ?: return
            indexSink.occurrence(KEY, key)
        }

        /**
         * Returns `include!()` macro call which includes [file]
         */
        fun getIncludedFrom(file: RsFile): RsMacroCall? {
            val originalFile = file.originalFile as? RsFile ?: return null
            return CachedValuesManager.getCachedValue(originalFile, INCLUDING_MOD_KEY) {
                CachedValueProvider.Result.create(
                    getIncludedFromInternal(originalFile),
                    originalFile.rustStructureOrAnyPsiModificationTracker
                )
            }
        }

        private fun getIncludedFromInternal(file: RsFile): RsMacroCall? {
            return makeIndexLookup(IncludeMacroKey(file.name), file)
                ?: makeIndexLookup(IncludeMacroKey.UNKNOWN_FILE_NAME, file)
        }

        private fun makeIndexLookup(key: IncludeMacroKey, file: RsFile): RsMacroCall? =
            recursionGuard(file, {
                val project = file.project
                checkCommitIsNotInProgress(project)

                var includedFrom: RsMacroCall? = null
                val scope = RsWithMacrosProjectScope(project)
                StubIndex.getInstance().processElements(KEY, key, project, scope, RsMacroCall::class.java) { macroCall ->
                    val includingFile = macroCall.findIncludingFile()
                    if (includingFile == file) {
                        includedFrom = macroCall
                        false
                    } else {
                        true
                    }
                }
                includedFrom
            })

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
