/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.macros

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.util.registry.RegistryValue
import com.intellij.openapi.util.registry.RegistryValueListener
import com.intellij.psi.stubs.*
import com.intellij.testFramework.ReadOnlyLightVirtualFile
import com.intellij.util.indexing.FileContent
import com.intellij.util.io.*
import org.rust.lang.RsLanguage
import org.rust.lang.core.macros.MacroExpansionSharedCache.Companion.CACHE_ENABLED
import org.rust.lang.core.psi.RsMacro
import org.rust.lang.core.psi.RsMacroCall
import org.rust.lang.core.psi.ext.bodyHash
import org.rust.lang.core.stubs.RsFileStub
import org.rust.stdext.HashCode
import java.io.DataInput
import java.io.DataOutput
import java.io.IOException
import java.nio.file.Path
import java.util.*
import java.util.concurrent.atomic.AtomicReference

/**
 * A persistent (stored on disk, in the real file system) cache for macro expansion text and stubs.
 * The cache is shared between different [Project]s (i.e. it's an application service).
 *
 * Used in a couple with [MacroExpansionStubsProvider] in order to provide stub cache.
 * The cache can be invalidated by usual "Invalidate Caches / Restart..." action (handled by
 * [RsMacroExpansionCachesInvalidator] because the cache is located in [getBaseMacroDir]).
 *
 * ## Implementation details
 *
 * The implementation is a bit tricky: the persistent cache is accessed via nullable atomic variable [data].
 * Such design is chosen because any operation with [PersistentHashMap] can lead to [IOException]
 * (since it's filesystem-based hash map), so this is a way to recover on possible errors.
 * For now, we just disable the cache (set [data] to `null`) if [IOException] occurs.
 * Also, the cache can be disabled via [CACHE_ENABLED] registry option.
 */
@Suppress("UnstableApiUsage")
@Service
class MacroExpansionSharedCache : Disposable {
    private val sm: SerializationManagerEx = SerializationManagerEx.getInstanceEx()
    private val ex: StubForwardIndexExternalizer<*> = StubForwardIndexExternalizer.createFileLocalExternalizer()

    private val data: AtomicReference<PersistentCacheData?> =
        AtomicReference(if (CACHE_ENABLED.asBoolean()) tryCreateData() else null)

    init {
        // Allows to enable/disable the cache without IDE restart
        CACHE_ENABLED.addListener(object : RegistryValueListener {
            override fun afterValueChanged(value: RegistryValue) {
                if (value.asBoolean()) {
                    val newData = tryCreateData()
                    if (newData != null && !data.compareAndSet(null, newData)) {
                        newData.close()
                    }
                } else {
                    data.compareAndExchange(data.get(), null)?.close()
                }
            }
        }, this)
    }

    private fun tryCreateData() = PersistentCacheData.tryCreate(getBaseMacroDir().resolve("cache"), sm, ex)

    val isEnabled: Boolean
        get() = CACHE_ENABLED.asBoolean() && data.get() != null

    override fun dispose() {
        do {
            val lastData = data.get()
            lastData?.close()
        } while (!data.compareAndSet(lastData, null))
    }

    fun flush() {
        data.get()?.flush()
    }

    private fun <Key, Value : Any> getOrPut(
        getMap: (PersistentCacheData) -> PersistentHashMap<Key, Value>,
        key: Key,
        computeValue: () -> Value?,
    ): Value? {
        if (!CACHE_ENABLED.asBoolean()) return computeValue()

        val data = data.get() ?: return computeValue()
        val map = getMap(data)

        val existingValue = try {
            map.get(key)
        } catch (e: IOException) {
            onError(data, e)
            return computeValue()
        }

        return if (existingValue != null) {
            existingValue
        } else {
            val newValue: Value = computeValue() ?: return null

            try {
                map.put(key, newValue)
            } catch (e: IOException) {
                onError(data, e)
            }

            newValue
        }
    }

    private fun onError(lastData: PersistentCacheData, e: IOException) {
        if (data.compareAndSet(lastData, null)) {
            lastData.close()
        }
        MACRO_LOG.warn(e)
    }

    fun cachedExpand(expander: MacroExpander, def: RsMacroDataWithHash, call: RsMacroCall): ExpansionResult? {
        val callData = RsMacroCallData(call)
        val hash = HashCode.mix(def.bodyHash ?: return null, call.bodyHash ?: return null)
        return cachedExpand(expander, def.data, callData, hash)
    }

    private fun cachedExpand(
        expander: MacroExpander,
        def: RsMacroData,
        call: RsMacroCallData,
        /** mixed hash of [def] and [call], passed as optimization */
        hash: HashCode
    ): ExpansionResult? {
        return getOrPut(PersistentCacheData::expansions, hash) {
            val result = expander.expandMacroAsText(def, call) ?: return@getOrPut null
            ExpansionResult(result.first.toString(), result.second)
        }
    }

    fun cachedBuildStub(fileContent: FileContent, hash: HashCode): SerializedStubTree? {
        return cachedBuildStub(hash) { fileContent }
    }

    private fun cachedBuildStub(hash: HashCode, fileContent: () -> FileContent?): SerializedStubTree? {
        return getOrPut(PersistentCacheData::stubs, hash) {
            val stub = StubTreeBuilder.buildStubTree(fileContent() ?: return@getOrPut null) ?: return@getOrPut null
            SerializedStubTree.serializeStub(stub, sm, ex)
        }
    }

    fun createExpansionStub(
        project: Project,
        expander: MacroExpander,
        def: RsMacroDataWithHash,
        call: RsMacroCallDataWithHash
    ): Pair<RsFileStub, ExpansionResult>? {
        val hash = HashCode.mix(def.bodyHash ?: return null, call.bodyHash ?: return null)
        val result = cachedExpand(expander, def.data, call.data, hash) ?: return null
        val stub = cachedBuildStub(hash) {
            val file = ReadOnlyLightVirtualFile("macro.rs", RsLanguage, result.text)
            createFileContent(project, file, result.text)
        } ?: return null
        return Pair(stub.stub as RsFileStub, result)
    }

    companion object {
        @JvmStatic
        fun getInstance(): MacroExpansionSharedCache = service()

        @JvmStatic
        private val CACHE_ENABLED = Registry.get("org.rust.lang.macros.persistentCache")
    }
}

class RsMacroDataWithHash(val data: RsMacroData, val bodyHash: HashCode?) {
    constructor(def: RsMacro) : this(RsMacroData(def), def.bodyHash)
}

class RsMacroCallDataWithHash(val data: RsMacroCallData, val bodyHash: HashCode?)

@Suppress("UnstableApiUsage")
private class PersistentCacheData(
    baseDir: Path,
    sm: SerializationManagerEx,
    ex: StubForwardIndexExternalizer<*>,
) {
    val expansions: PersistentHashMap<HashCode, ExpansionResult> = newPersistentHashMap(
        baseDir.resolve("expansion-cache"),
        HashCodeKeyDescriptor,
        ExpansionResultExternalizer,
        1 * 1024 * 1024,
        MacroExpander.EXPANDER_VERSION
    )

    val stubs: PersistentHashMap<HashCode, SerializedStubTree> = newPersistentHashMap(
        baseDir.resolve("expansion-stubs-cache"),
        HashCodeKeyDescriptor,
        newSerializedStubTreeDataExternalizer(sm, ex),
        1 * 1024 * 1024,
        MacroExpander.EXPANDER_VERSION + RsFileStub.Type.stubVersion
    )

    fun flush() {
        expansions.force()
        stubs.force()
    }

    fun close() {
        catchAndWarn(expansions::close)
        catchAndWarn(stubs::close)
    }

    companion object {
        @JvmStatic
        fun tryCreate(
            baseDir: Path,
            sm: SerializationManagerEx,
            ex: StubForwardIndexExternalizer<*>,
        ): PersistentCacheData? = try {
            MacroExpansionManager.checkInvalidatedStorage()
            PersistentCacheData(baseDir, sm, ex)
        } catch (e: IOException) {
            MACRO_LOG.warn(e)
            null
        }

        @JvmStatic
        private fun <K, V> newPersistentHashMap(
            file: Path,
            keyDescriptor: KeyDescriptor<K>,
            valueExternalizer: DataExternalizer<V>,
            initialSize: Int,
            version: Int,
        ): PersistentHashMap<K, V> {
            return IOUtil.openCleanOrResetBroken(
                { PersistentHashMap(file, keyDescriptor, valueExternalizer, initialSize, version) },
                file
            )
        }

        @JvmStatic
        private fun catchAndWarn(runnable: () -> Unit) {
            try {
                runnable()
            } catch (e: IOException) {
                MACRO_LOG.warn(e)
            }
        }
    }
}

private object HashCodeKeyDescriptor : KeyDescriptor<HashCode>, DifferentSerializableBytesImplyNonEqualityPolicy {
    override fun getHashCode(value: HashCode): Int {
        return value.hashCode()
    }

    override fun isEqual(val1: HashCode?, val2: HashCode?): Boolean {
        return Objects.equals(val1, val2)
    }

    override fun save(out: DataOutput, value: HashCode) {
        out.write(value.toByteArray())
    }

    override fun read(inp: DataInput): HashCode {
        val bytes = ByteArray(HashCode.ARRAY_LEN)
        inp.readFully(bytes)
        return HashCode.fromByteArray(bytes)
    }
}

data class ExpansionResult(
    val text: String,
    val ranges: RangeMap,
)

private object ExpansionResultExternalizer : DataExternalizer<ExpansionResult> {
    override fun save(out: DataOutput, value: ExpansionResult) {
        IOUtil.writeUTF(out, value.text)
        value.ranges.writeTo(out)
    }

    override fun read(inp: DataInput): ExpansionResult {
        return ExpansionResult(
            IOUtil.readUTF(inp),
            RangeMap.readFrom(inp)
        )
    }
}
