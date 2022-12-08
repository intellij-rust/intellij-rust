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
import com.intellij.psi.stubs.*
import com.intellij.testFramework.ReadOnlyLightVirtualFile
import com.intellij.util.indexing.FileContent
import com.intellij.util.indexing.FileContentImpl
import com.intellij.util.io.*
import org.rust.lang.RsLanguage
import org.rust.lang.core.macros.decl.DeclMacroExpander
import org.rust.lang.core.macros.decl.MACRO_DOLLAR_CRATE_IDENTIFIER
import org.rust.lang.core.macros.decl.MACRO_DOLLAR_CRATE_IDENTIFIER_REGEX
import org.rust.lang.core.macros.errors.*
import org.rust.lang.core.macros.proc.ProcMacroExpander
import org.rust.lang.core.parser.RustParserDefinition
import org.rust.lang.core.stubs.RsFileStub
import org.rust.stdext.*
import org.rust.stdext.RsResult.Err
import org.rust.stdext.RsResult.Ok
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
 */
@Suppress("UnstableApiUsage")
@Service
class MacroExpansionSharedCache : Disposable {
    private val globalSerMgr: SerializationManagerEx = SerializationManagerEx.getInstanceEx()
    private val stubExternalizer: StubForwardIndexExternalizer<*> =
        StubForwardIndexExternalizer.createFileLocalExternalizer()

    private val data: AtomicReference<PersistentCacheData?> =
        AtomicReference(tryCreateData())

    private fun tryCreateData() = PersistentCacheData.tryCreate(getBaseMacroDir().resolve("cache"), stubExternalizer)

    val isEnabled: Boolean
        get() = data.get() != null

    override fun dispose() {
        do {
            val lastData = data.get()
            lastData?.close()
        } while (!data.compareAndSet(lastData, null))
    }

    fun flush() {
        data.get()?.flush()
    }

    private fun <Key, Value> getOrPut(
        getMap: (PersistentCacheData) -> PersistentHashMap<Key, Value>,
        cacheRetrievingStrategy: CacheRetrievingStrategy<Value>,
        key: Key,
        computeValue: (SerializationManagerEx) -> Value,
    ): Value {
        val data = data.get() ?: return computeValue(globalSerMgr)
        val map = getMap(data)

        val existingValue = try {
            map.get(key)
        } catch (e: IOException) {
            onError(data, e)
            return computeValue(globalSerMgr)
        }

        return if (existingValue != null && cacheRetrievingStrategy.isRetrievedValueReusable(existingValue)) {
            existingValue
        } else {
            val newValue = computeValue(data.localSerMgr)

            try {
                map.put(key, newValue)
            } catch (e: IOException) {
                onError(data, e)
            }

            newValue
        }
    }

    private interface CacheRetrievingStrategy<in T> {
        /**
         * The [value] is just retrieved from the cache.
         * If returns `true`, the [value] is used.
         * If returns `false`, the [value] is dropped and new value is computed and cached.
         */
        fun isRetrievedValueReusable(value: T): Boolean
    }

    private object RetrieveEverythingStrategy : CacheRetrievingStrategy<Any?> {
        override fun isRetrievedValueReusable(value: Any?): Boolean = true
    }

    private object DontRetrieveSomeErrorsStrategy : CacheRetrievingStrategy<RsResult<ExpansionResultOk, MacroExpansionError>> {
        override fun isRetrievedValueReusable(value: RsResult<ExpansionResultOk, MacroExpansionError>): Boolean {
            return when (value) {
                is Ok -> true
                is Err -> value.err.canCacheError()
            }
        }
    }

    private fun onError(lastData: PersistentCacheData, e: IOException) {
        if (data.compareAndSet(lastData, null)) {
            lastData.close()
        }
        MACRO_LOG.warn(e)
    }

    /**
     * Note: here we're caching all possible results (hence they are available using [getExpansionIfCached]),
     * but (thanks to [DontRetrieveSomeErrorsStrategy]) we're not retrieving some *error* results from the
     * cache (i.e. recompute them all the time [cachedExpand] is used). This is done for the most of the
     * procedural macro expansion errors [ProcMacroExpansionError] (because proc macros are pretty unstable
     * and a subsequent macro invocation may be successful)
     */
    fun <T : RsMacroData, E : MacroExpansionError> cachedExpand(
        expander: MacroExpander<T, E>,
        def: T,
        call: RsMacroCallData,
        /** mixed hash of [def] and [call] (see [RsMacroDataWithHash.mixHash]), passed as optimization */
        mixHash: HashCode
    ): RsResult<ExpansionResultOk, E> {
        return getOrPut(PersistentCacheData::expansions, DontRetrieveSomeErrorsStrategy, mixHash) {
            expander.expandMacroAsTextWithErr(def, call)
                .map { ExpansionResultOk(it.first.toString(), it.second) }
        }.mapErr {
            // It's strictly impossible that `expander` returns an error other than `E`, but in theory
            // the cache can be outdated or corrupted in such a way that it returns another error
            // for the same `hash`
            @Suppress("UNCHECKED_CAST")
            it as E
        }
    }

    fun getExpansionIfCached(hash: HashCode): RsResult<ExpansionResultOk, MacroExpansionError>? {
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

    private fun cachedBuildStub(hash: HashCode, fileContent: () -> FileContent): SerializedStubTree? {
        return getOrPut(PersistentCacheData::stubs, RetrieveEverythingStrategy, hash) { serMgr ->
            val fc = fileContent()
            val stub = StubTreeBuilder.buildStubTree(fc)
            // e.g. large expansion which is parsed as plain text (and not as Rust file)
                ?: return@getOrPut null
            SerializedStubTree.serializeStub(stub, serMgr, stubExternalizer)
        }
    }

    fun <T : RsMacroData, E : MacroExpansionError> createExpansionStub(
        project: Project,
        expander: MacroExpander<T, E>,
        def: T,
        call: RsMacroCallData,
        /** mixed hash of [def] and [call] (see [RsMacroDataWithHash.mixHash]), passed as optimization */
        mixHash: HashCode
    ): RsResult<Pair<RsFileStub?, ExpansionResultOk>, E> {
        val result = cachedExpand(expander, def, call, mixHash).unwrapOrElse { return Err(it) }
        val serializedStub = cachedBuildStub(mixHash) {
            val file = ReadOnlyLightVirtualFile("macro.rs", RsLanguage, result.text)
            FileContentImpl.createByText(file, result.text, project)
        } ?: return Ok(null to result)

        val stub = try {
            serializedStub.stub
        } catch (e: SerializerNotFoundException) {
            // This most likely means that `RsFileStub.Type.stubVersion` was not incremented after stubs change
            MACRO_LOG.error(e)
            return Ok(null to result)
        }

        if (stub === SerializedStubTree.NO_STUB) {
            return Ok(null to result)
        }

        return Ok(stub as RsFileStub to result)
    }

    companion object {
        @JvmStatic
        fun getInstance(): MacroExpansionSharedCache = service()
    }
}

@Suppress("UnstableApiUsage")
private class PersistentCacheData(
    val localSerMgr: SerializationManagerImpl,
    val expansions: PersistentHashMap<HashCode, RsResult<ExpansionResultOk, MacroExpansionError>>,
    val stubs: PersistentHashMap<HashCode, SerializedStubTree?>
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
                        localSerMgr.repairNameStorage(Exception("The storage has already been corrupted"))
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
                        + RustParserDefinition.PARSER_VERSION + 2
                )
                cleaners += expansions::close

                val stubs = newPersistentHashMap(
                    stubCacheFile,
                    HashCodeKeyDescriptor,
                    NullableExternalizer(SerializedStubTreeDataExternalizer(localSerMgr, stubExternalizer)),
                    1 * 1024 * 1024,
                    DeclMacroExpander.EXPANDER_VERSION + ProcMacroExpander.EXPANDER_VERSION
                        + RsFileStub.Type.stubVersion + 1
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

    @Throws(IOException::class)
    override fun save(out: DataOutput, value: HashCode) {
        out.write(value.toByteArray())
    }

    @Throws(IOException::class)
    override fun read(inp: DataInput): HashCode {
        val bytes = ByteArray(HashCode.ARRAY_LEN)
        inp.readFully(bytes)
        return HashCode.fromByteArray(bytes)
    }
}

class ExpansionResultOk(
    val text: String,
    val ranges: RangeMap,
    /** Optimization: occurrences of [MACRO_DOLLAR_CRATE_IDENTIFIER] */
    val dollarCrateOccurrences: IntArray = MACRO_DOLLAR_CRATE_IDENTIFIER_REGEX.findAll(text)
        .mapTo(mutableListOf()) { it.range.first }
        .toIntArray()
)

fun ExpansionResultOk.shiftDstRangesRight(offset: Int): ExpansionResultOk {
    val dollarCrateOccurrences = dollarCrateOccurrences.map { it + offset }.toIntArray()
    val ranges = ranges.ranges.map { it.dstShiftRight(offset) }
    return ExpansionResultOk(text, RangeMap(ranges), dollarCrateOccurrences)
}

private object ExpansionResultExternalizer : DataExternalizer<RsResult<ExpansionResultOk, MacroExpansionError>> {
    @Throws(IOException::class)
    override fun save(out: DataOutput, value: RsResult<ExpansionResultOk, MacroExpansionError>) {
        out.writeRsResult(value, DataOutput::saveExpansionResultOk, DataOutput::writeMacroExpansionError)
    }

    @Throws(IOException::class)
    override fun read(inp: DataInput): RsResult<ExpansionResultOk, MacroExpansionError> {
        return inp.readRsResult(DataInput::readExpansionResultOk, DataInput::readMacroExpansionError)
    }
}

private class NullableExternalizer<T : Any>(private val inner: DataExternalizer<T>) : DataExternalizer<T?> {
    @Throws(IOException::class)
    override fun save(out: DataOutput, value: T?) {
        if (value != null) {
            out.writeBoolean(true)
            inner.save(out, value)
        } else {
            out.writeBoolean(false)
        }
    }

    @Throws(IOException::class)
    override fun read(inp: DataInput): T? {
        return if (inp.readBoolean()) {
            inner.read(inp)
        } else {
            null
        }
    }
}


@Throws(IOException::class)
private fun DataOutput.saveExpansionResultOk(value: ExpansionResultOk) {
    IOUtil.writeUTF(this, value.text)
    value.ranges.writeTo(this)
    writeIntArray(value.dollarCrateOccurrences)
}

@Throws(IOException::class)
private fun DataInput.readExpansionResultOk(): ExpansionResultOk {
    return ExpansionResultOk(
        IOUtil.readUTF(this),
        RangeMap.readFrom(this),
        readIntArray()
    )
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
