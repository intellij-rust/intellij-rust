/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.macros

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.util.registry.RegistryValue
import com.intellij.openapi.util.registry.RegistryValueListener
import com.intellij.psi.stubs.*
import com.intellij.testFramework.ReadOnlyLightVirtualFile
import com.intellij.util.indexing.FileContent
import com.intellij.util.indexing.FileContentImpl
import com.intellij.util.io.*
import org.rust.lang.RsLanguage
import org.rust.lang.core.macros.MacroExpansionSharedCache.Companion.CACHE_ENABLED
import org.rust.lang.core.macros.decl.*
import org.rust.lang.core.macros.proc.ProcMacroExpander
import org.rust.lang.core.parser.RustParserDefinition
import org.rust.lang.core.stubs.RsFileStub
import org.rust.stdext.HashCode
import org.rust.stdext.readVarInt
import org.rust.stdext.writeVarInt
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
    private val globalSerMgr: SerializationManagerEx = SerializationManagerEx.getInstanceEx()
    private val stubExternalizer: StubForwardIndexExternalizer<*> =
        StubForwardIndexExternalizer.createFileLocalExternalizer()

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

    private fun tryCreateData() = PersistentCacheData.tryCreate(getBaseMacroDir().resolve("cache"), stubExternalizer)

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
        computeValue: (SerializationManagerEx) -> Value?,
    ): Value? {
        if (!CACHE_ENABLED.asBoolean()) return computeValue(globalSerMgr)

        val data = data.get() ?: return computeValue(globalSerMgr)
        val map = getMap(data)

        val existingValue = try {
            map.get(key)
        } catch (e: IOException) {
            onError(data, e)
            return computeValue(globalSerMgr)
        }

        return if (existingValue != null) {
            existingValue
        } else {
            val newValue: Value = computeValue(data.localSerMgr) ?: return null

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

    fun <T : RsMacroData> cachedExpand(
        expander: MacroExpander<T, *>,
        def: T,
        call: RsMacroCallData,
        /** mixed hash of [def] and [call], passed as optimization */
        hash: HashCode
    ): ExpansionResult? {
        return getOrPut(PersistentCacheData::expansions, hash) {
            val result = expander.expandMacroAsText(def, call) ?: return@getOrPut null
            ExpansionResult(result.first.toString(), result.second)
        }
    }

    fun getExpansionIfCached(hash: HashCode): ExpansionResult? {
        if (!CACHE_ENABLED.asBoolean()) return null
        val data = data.get() ?: return null
        val map = data.expansions
        return try {
            map.get(hash)
        } catch (e: IOException) {
            onError(data, e)
            null
        }
    }

    fun cachedBuildStub(fileContent: FileContent, hash: HashCode): SerializedStubTree? {
        return cachedBuildStub(hash) { fileContent }
    }

    private fun cachedBuildStub(hash: HashCode, fileContent: () -> FileContent?): SerializedStubTree? {
        return getOrPut(PersistentCacheData::stubs, hash) { serMgr ->
            val stub = StubTreeBuilder.buildStubTree(fileContent() ?: return@getOrPut null) ?: return@getOrPut null
            SerializedStubTree.serializeStub(stub, serMgr, stubExternalizer)
        }
    }

    fun <T : RsMacroData> createExpansionStub(
        project: Project,
        expander: MacroExpander<T, *>,
        def: RsMacroDataWithHash<T>,
        call: RsMacroCallDataWithHash
    ): Pair<RsFileStub, ExpansionResult>? {
        val hash = def.mixHash(call) ?: return null
        val result = cachedExpand(expander, def.data, call.data, hash) ?: return null
        val serializedStub = cachedBuildStub(hash) {
            val file = ReadOnlyLightVirtualFile("macro.rs", RsLanguage, result.text)
            FileContentImpl.createByText(file, result.text).also {
                (it as FileContentImpl).project = project
            }
        } ?: return null

        val stub = try {
            serializedStub.stub
        } catch (e: SerializerNotFoundException) {
            // This most likely means that `RsFileStub.Type.stubVersion` was not incremented after stubs change
            MACRO_LOG.error(e)
            return null
        }

        if (stub === SerializedStubTree.NO_STUB) {
            return null
        }

        return Pair(stub as RsFileStub, result)
    }

    companion object {
        @JvmStatic
        fun getInstance(): MacroExpansionSharedCache = service()

        @JvmStatic
        private val CACHE_ENABLED = Registry.get("org.rust.lang.macros.persistentCache")
    }
}

@Suppress("UnstableApiUsage")
private class PersistentCacheData(
    val localSerMgr: SerializationManagerImpl,
    val expansions: PersistentHashMap<HashCode, ExpansionResult>,
    val stubs: PersistentHashMap<HashCode, SerializedStubTree>
) {
    fun flush() {
        localSerMgr.flushNameStorage()
        expansions.force()
        stubs.force()
    }

    fun close() {
        catchAndWarn(expansions::close)
        catchAndWarn(stubs::close)
        Disposer.dispose(localSerMgr)
    }

    companion object {
        @JvmStatic
        fun tryCreate(baseDir: Path, stubExternalizer: StubForwardIndexExternalizer<*>): PersistentCacheData? {
            MacroExpansionManager.checkInvalidatedStorage()

            val cleaners = mutableListOf<() -> Unit>()

            return try {
                val namesFile = baseDir.resolve("stub.names")
                val stubCacheFile = baseDir.resolve("expansion-stubs-cache")

                val namesFileExists = namesFile.exists()

                val localSerMgr = SerializationManagerImpl(namesFile, false)
                cleaners.add { Disposer.dispose(localSerMgr) }

                if (!namesFileExists || localSerMgr.isNameStorageCorrupted) {
                    if (localSerMgr.isNameStorageCorrupted) {
                        localSerMgr.repairNameStorage()
                        if (localSerMgr.isNameStorageCorrupted) {
                            throw IOException("Serialization Manager is corrupted after repair")
                        }
                    }
                    IOUtil.deleteAllFilesStartingWith(stubCacheFile.toFile())
                }

                val expansions = newPersistentHashMap(
                    baseDir.resolve("expansion-cache"),
                    HashCodeKeyDescriptor,
                    ExpansionResultExternalizer,
                    1 * 1024 * 1024,
                    DeclMacroExpander.EXPANDER_VERSION + ProcMacroExpander.EXPANDER_VERSION
                        + RustParserDefinition.PARSER_VERSION + 1
                )
                cleaners += expansions::close

                val stubs = newPersistentHashMap(
                    stubCacheFile,
                    HashCodeKeyDescriptor,
                    SerializedStubTreeDataExternalizer(localSerMgr, stubExternalizer),
                    1 * 1024 * 1024,
                    DeclMacroExpander.EXPANDER_VERSION + ProcMacroExpander.EXPANDER_VERSION
                        + RsFileStub.Type.stubVersion
                )
                cleaners += stubs::close

                PersistentCacheData(localSerMgr, expansions, stubs)
            } catch (e: IOException) {
                MACRO_LOG.warn(e)
                cleaners.forEach(::catchAndWarn)
                null
            }
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
            } catch (t: Throwable) {
                MACRO_LOG.error(t)
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

class ExpansionResult(
    val text: String,
    val ranges: RangeMap,
    /** Optimization: occurrences of [MACRO_DOLLAR_CRATE_IDENTIFIER] */
    val dollarCrateOccurrences: IntArray = MACRO_DOLLAR_CRATE_IDENTIFIER_REGEX.findAll(text)
        .mapTo(mutableListOf()) { it.range.first }
        .toIntArray()
)

private object ExpansionResultExternalizer : DataExternalizer<ExpansionResult> {
    override fun save(out: DataOutput, value: ExpansionResult) {
        IOUtil.writeUTF(out, value.text)
        value.ranges.writeTo(out)
        out.writeIntArray(value.dollarCrateOccurrences)
    }

    override fun read(inp: DataInput): ExpansionResult {
        return ExpansionResult(
            IOUtil.readUTF(inp),
            RangeMap.readFrom(inp),
            inp.readIntArray()
        )
    }
}

private fun DataOutput.writeIntArray(array: IntArray) {
    writeVarInt(array.size)
    for (element in array) {
        writeVarInt(element)
    }
}

private fun DataInput.readIntArray(): IntArray {
    val size = readVarInt()
    return IntArray(size) { readVarInt() }
}
