/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.macros

import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.VirtualFileWithId
import com.intellij.psi.PsiAnchor
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiManager
import com.intellij.psi.StubBasedPsiElement
import com.intellij.reference.SoftReference
import gnu.trove.TIntObjectHashMap
import org.rust.cargo.project.workspace.PackageOrigin
import org.rust.lang.RsFileType
import org.rust.lang.core.macros.MacroExpansionManagerImpl.Testmarks
import org.rust.lang.core.psi.RsFile
import org.rust.lang.core.psi.RsMacro
import org.rust.lang.core.psi.RsMacroCall
import org.rust.lang.core.psi.ext.bodyHash
import org.rust.lang.core.psi.ext.containingCargoTarget
import org.rust.lang.core.psi.ext.resolveToMacro
import org.rust.lang.core.psi.ext.stubDescendantsOfTypeStrict
import org.rust.lang.core.resolve.DEFAULT_RECURSION_LIMIT
import org.rust.lang.core.stubs.RsFileStub
import org.rust.openapiext.*
import org.rust.stdext.HashCode
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.FileNotFoundException
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.DeflaterOutputStream
import java.util.zip.InflaterInputStream

class ExpandedMacroStorage(val project: Project) {
    // Data structures are guarded by the platform RWLock
    private val sourceFiles: TIntObjectHashMap<SourceFile> = TIntObjectHashMap()
    private val expandedFileToInfo: TIntObjectHashMap<ExpandedMacroInfo> = TIntObjectHashMap()
    private val stepped: Array<MutableList<SourceFile>?> = arrayOfNulls(DEFAULT_RECURSION_LIMIT)

    val isEmpty: Boolean get() = sourceFiles.isEmpty

    private fun deserialize(sfs: List<SourceFile>) {
        for (sf in sfs) {
            sourceFiles.put(sf.fileId, sf)
            getOrPutStagedList(sf).add(sf)
            sf.forEachInfo { if (it.fileId > 0) expandedFileToInfo.put(it.fileId, it) }
        }
    }

    fun clear() {
        checkWriteAccessAllowed()
        sourceFiles.clear()
        expandedFileToInfo.clear()
        stepped.fill(null)
    }

    fun processExpandedMacroInfos(action: (ExpandedMacroInfo) -> Unit) {
        checkReadAccessAllowed()
        expandedFileToInfo.forEachValue { action(it); true }
    }

    private fun getOrPutStagedList(sf: SourceFile): MutableList<SourceFile> {
        var list = stepped[sf.depth]
        if (list == null) {
            list = mutableListOf()
            stepped[sf.depth] = list
        }
        return list
    }

    fun makeExpansionTask(map: Map<VirtualFile, List<RsMacroCall>>): Sequence<List<Extractable>> {
        val v2 = WriteAction.computeAndWait<List<Pair<SourceFile, List<RsMacroCall>>>, Throwable> {
            map.entries.mapNotNull { (file, calls) ->
                getOrCreateSourceFile(file)?.let { it to calls }
            }
        }
        return runReadActionInSmartMode(project) {
            v2.forEach { (file, calls) -> file.rebind(calls) }
            makeValidationTask()
        }
    }

    fun makeValidationTask(workspaceOnly: Boolean = false): Sequence<List<Extractable>> {
        checkReadAccessAllowed()

        return stepped.asSequence().map { step ->
            step?.map { sf ->
                object : Extractable {
                    override fun extract(): List<Pipeline.Stage1ResolveAndExpand> {
                        return sf.extract(workspaceOnly)
                    }
                }
            } ?: emptyList()
        }
    }

    fun addExpandedMacro(call: RsMacroCall, oldInfo: ExpandedMacroInfo?, def: RsMacro?, expansion: VirtualFile?): ExpandedMacroInfo {
        checkWriteAccessAllowed()

        if (oldInfo != null) {
            removeInvalidInfo(oldInfo, false)
        }

        val callFile = call.containingFile.virtualFile
        check(callFile.fileSystem is LocalFileSystem)
        val sourceFile = getOrCreateSourceFile(callFile) ?: error("too deep expansion (unreachable)")
        if (oldInfo != null && oldInfo.sourceFile != sourceFile) {
            error("unreachable")
        }
        val info = ExpandedMacroInfo(
            sourceFile,
            expansion,
            def?.bodyHash,
            call.bodyHash,
            oldInfo?.stubIndex ?: -1
        )

        info.bindTo(call)

        sourceFile.addInfo(info)
        if (info.fileId > 0) expandedFileToInfo.put(info.fileId, info)
        info.expansionFile?.let { getOrCreateSourceFile(it) }

        return info
    }

    fun removeInvalidInfo(oldInfo: ExpandedMacroInfo, clean: Boolean) {
        checkWriteAccessAllowed()

        expandedFileToInfo.remove(oldInfo.fileId)
        val sf = oldInfo.sourceFile
        sf.removeInfo(oldInfo)
        if (clean && sf.isEmpty()) {
            sourceFiles.remove(sf.fileId)
            stepped[sf.depth]?.remove(sf)
        }
    }

    fun getOrCreateSourceFile(callFile: VirtualFile): SourceFile? {
        checkWriteAccessAllowed()
        val fileId = callFile.fileId
        return sourceFiles[fileId] ?: run {
            val depth = (getInfoForExpandedFile(callFile)?.sourceFile?.depth ?: -1) + 1
            if (depth >= DEFAULT_RECURSION_LIMIT) return null
            val sf = SourceFile(this, callFile, depth)
            getOrPutStagedList(sf).add(sf)
            sourceFiles.put(fileId, sf)
            sf
        }
    }

    fun getSourceFile(callFile: VirtualFile): SourceFile? {
        checkReadAccessAllowed()
        return sourceFiles[callFile.fileId]
    }

    fun getInfoForExpandedFile(file: VirtualFile): ExpandedMacroInfo? {
        checkReadAccessAllowed()
        return expandedFileToInfo[file.fileId]
    }

    fun getInfoForCall(call: RsMacroCall): ExpandedMacroInfo? {
        checkReadAccessAllowed()

        val file = call.containingFile.virtualFile ?: return null
        val sf = getSourceFile(file) ?: return null

        return sf.getInfoForCall(call)
    }

    fun unbindPsi() {
        checkReadAccessAllowed()
        sourceFiles.forEachValue { it.unbindPsi(); true }
    }

    companion object {
        private val LOG = Logger.getInstance(ExpandedMacroStorage::class.java)
        private const val STORAGE_VERSION = 5

        fun load(project: Project, dataFile: Path): ExpandedMacroStorage? {
            return try {
                DataInputStream(InflaterInputStream(Files.newInputStream(dataFile))).use { data ->
                    if (data.readInt() != STORAGE_VERSION) return null
                    if (data.readInt() != RsFileStub.Type.stubVersion) return null

                    val sourceFilesSize = data.readInt()
                    val sourceFiles = ArrayList<SourceFile>(sourceFilesSize)

                    val storage = ExpandedMacroStorage(project)
                    for (i in 0 until sourceFilesSize) {
                        sourceFiles += SourceFile.readFrom(data, storage) ?: continue
                    }
                    storage.deserialize(sourceFiles)
                    storage
                }
            } catch (e: FileNotFoundException) {
                null
            } catch (e: java.nio.file.NoSuchFileException) {
                null
            } catch (e: Exception) {
                LOG.warn(e)
                null
            }
        }

        fun saveStorage(storage: ExpandedMacroStorage, dataFile: Path) {
            Files.createDirectories(dataFile.parent)
            DataOutputStream(DeflaterOutputStream(Files.newOutputStream(dataFile))).use { data ->
                data.writeInt(STORAGE_VERSION)
                data.writeInt(RsFileStub.Type.stubVersion)

                data.writeInt(storage.sourceFiles.size())
                storage.sourceFiles.forEachValue { it.writeTo(data); true }
            }
        }
    }
}

class SourceFile(
    val storage: ExpandedMacroStorage,
    val file: VirtualFile,
    val depth: Int,
    private var fresh: Boolean = true,
    private var modificationStamp: Long = -1,
    private val infos: MutableList<ExpandedMacroInfo> = mutableListOf()
) {
    private var cachedPsi: SoftReference<RsFile>? = null

    private val fileUrl: String get() = file.url
    val fileId: Int get() = file.fileId

    val project: Project
        get() = storage.project

    private val rootSourceFile: SourceFile by lazy(LazyThreadSafetyMode.PUBLICATION) {
        findRootSourceFile()
    }

    // Should not be lazy b/c target/pkg/origin can be changed
    private val isBelongToWorkspace: Boolean
        get() = rootSourceFile.loadPsi()?.containingCargoTarget?.pkg?.origin == PackageOrigin.WORKSPACE

    @Synchronized
    fun forEachInfo(action: (ExpandedMacroInfo) -> Unit) {
        infos.forEach(action)
    }

    @Synchronized
    fun addInfo(info: ExpandedMacroInfo) {
        check(!fresh)
        infos.add(info)
        detectDuplicates()
    }

    @Synchronized
    fun removeInfo(info: ExpandedMacroInfo) {
        check(!fresh)
        infos.remove(info)
    }

    fun isEmpty(): Boolean =
        infos.isEmpty()

    fun extract(workspaceOnly: Boolean): List<Pipeline.Stage1ResolveAndExpand> {
        if (workspaceOnly && !isBelongToWorkspace) {
            // very hot path; the condition should be as fast as possible
            return emptyList()
        }

        synchronized(this) {
            rebind()
            return infos.map { info ->
                info.makePipeline()
            }
        }
    }

    @Synchronized
    fun rebind(calls: List<RsMacroCall>? = null) {
        checkReadAccessAllowed() // Needed to access PSI
        checkIsSmartMode(project)

        if (isBoundToPsi()) {
            check(!fresh)
            return // up to date
        }

        if (!file.isValid) return

        detectDuplicates()
        ProgressManager.getInstance().executeNonCancelableSection {
            when {
                fresh -> {
                    if (infos.isNotEmpty()) error("impossible")
                    freshRebind(calls)
                }
                areStubIndicesActual() && infos.all { it.stubIndex != -1 } -> {
                    stubBasedRebind()
                    detectDuplicates()
                }
                else -> {
                    hashBasedRebind(calls)
                    detectDuplicates()
                }
            }

            fresh = false
        }
    }

    private fun stubBasedRebind() {
        Testmarks.stubBasedRebind.hit()
        val psi = recoverPsi() ?: return
        for (info in infos) {
            val stubIndex = info.stubIndex
            if (stubIndex != -1) {
                val call = psi.stubbedSpine.getStubPsi(stubIndex) as RsMacroCall
                info.bindTo(call)
            }
        }
    }

    private fun freshRebind(prefetchedCalls: List<RsMacroCall>?) {
        val psi = recoverPsi() ?: return
        val calls = prefetchedCalls ?: psi.stubDescendantsOfTypeStrict<RsMacroCall>().filter { it.isTopLevelExpansion }
        for (call in calls) {
            val info1 = ExpandedMacroInfo(this, null, null, call.bodyHash)
            info1.bindAndUpdateStubIndex(call)
            this.infos.add(info1)
        }

        modificationStamp = getActualModStamp()
    }

    private fun hashBasedRebind(prefetchedCalls: List<RsMacroCall>?) {
        Testmarks.hashBasedRebind.hit()

        val psi = recoverPsi() ?: return
        val calls = prefetchedCalls ?: psi.stubDescendantsOfTypeStrict<RsMacroCall>().filter { it.isTopLevelExpansion }
        val unboundInfos = infos.toMutableList()
        unboundInfos.forEach {
            it.cachedMacroCall = null
            it.stubIndex = -1
        }
        val orphans = mutableListOf<RsMacroCall>()
        for (call in calls) {
            val info = unboundInfos.find { it.isUpToDate(call, call.resolveToMacro()) }
            if (info != null) {
                Testmarks.hashBasedRebindExactHit.hit()
                unboundInfos.remove(info)
                info.bindAndUpdateStubIndex(call)
            } else {
                orphans += call
            }
        }

        for (call in orphans) {
            val info = unboundInfos.find { it.callHash == call.bodyHash }
            if (info != null) {
                Testmarks.hashBasedRebindCallHit.hit()
                unboundInfos.remove(info)
                info.bindAndUpdateStubIndex(call)
            } else {
                Testmarks.hashBasedRebindNotHit.hit()
                val info1 = ExpandedMacroInfo(this, null, null, call.bodyHash)
                info1.bindAndUpdateStubIndex(call)
                this.infos.add(info1)
            }
        }

        modificationStamp = getActualModStamp()
    }

    private fun detectDuplicates() {
        if (!isUnitTestMode) return
        val set = mutableSetOf<RsMacroCall>()
        for (info in infos) {
            val element = info.getMacroCall() ?: continue
            if (!set.add(element)) {
                error("Duplicate ${element.text}")
            }
        }
    }

    private fun isBoundToPsi() =
        derefPsi() != null && (infos.isEmpty() || infos.any { it.cachedMacroCall?.get()?.isValid == true })

    private fun derefPsi(): RsFile? = cachedPsi?.get()

    private fun areStubIndicesActual() =
        file.isValid && modificationStamp != -1L && modificationStamp == getActualModStamp()

    private fun getActualModStamp(): Long {
        val document = FileDocumentManager.getInstance().getCachedDocument(file)
        return if (document != null) {
            PsiDocumentManager.getInstance(project).getLastCommittedStamp(document)
        } else {
            file.modificationStamp
        }
    }

    private fun recoverPsi(): RsFile? {
        val psi = loadPsi() ?: return null
        cachedPsi = SoftReference(psi)
        return psi
    }

    private fun loadPsi(): RsFile? =
        file.takeIf { it.isValid }?.toPsiFile(project) as? RsFile

    // TODO yep, it's O(n)
    @Synchronized
    fun getInfoForCall(call: RsMacroCall): ExpandedMacroInfo? {
        rebind()
        return infos.find { it.getMacroCall() == call }
    }

    fun invalidateStubIndices() {
        checkWriteAccessAllowed()
        cachedPsi = null
        modificationStamp = -1
    }

    @Synchronized
    fun unbindPsi() {
        cachedPsi = null
        for (info in infos) {
            info.cachedMacroCall = null
        }
    }

    @Synchronized
    fun updateStubIndices() {
        for (info in infos) {
            info.getMacroCall()?.let { info.stubIndex = calcStubIndex(it) }
        }
        modificationStamp = getActualModStamp()
    }

    @Synchronized
    fun writeTo(data: DataOutputStream) {
        data.apply {
            writeUTF(fileUrl)
            writeInt(depth)
            writeBoolean(fresh)
            writeLong(modificationStamp)
            writeInt(infos.size)
            infos.forEach { it.writeTo(data) }
        }
    }

    companion object {
        fun readFrom(data: DataInputStream, storage: ExpandedMacroStorage): SourceFile? {
            val fileUrl = data.readUTF()
            val depth = data.readInt()
            val fresh = data.readBoolean()
            val modificationStamp = data.readLong()
            val infosSize = data.readInt()
            val serInfos = (0 until infosSize).map { SerializedExpandedMacroInfo.readFrom(data) }

            val file = VirtualFileManager.getInstance().findFileByUrl(fileUrl) ?: return null
            val infos = ArrayList<ExpandedMacroInfo>(infosSize)
            val sf = SourceFile(
                storage,
                file,
                depth,
                fresh,
                modificationStamp,
                infos
            )
            serInfos.mapNotNullTo(infos) { it.toExpandedMacroInfo(sf) }
            return sf
        }
    }
}

private tailrec fun SourceFile.findRootSourceFile(): SourceFile {
    val file = file.takeIf { it.isValid } ?: return this
    val parentSF = storage.getInfoForExpandedFile(file)?.sourceFile ?: return this
    return parentSF.findRootSourceFile()
}

class ExpandedMacroInfo(
    val sourceFile: SourceFile,
    val expansionFile: VirtualFile?,
    private val defHash: HashCode?,
    val callHash: HashCode?,
    var stubIndex: Int = -1
) {
    private val expansionFileUrl: String? get() = expansionFile?.url
    val fileId: Int get() = expansionFile?.fileId ?: -1

    @Volatile
    var cachedMacroCall: SoftReference<RsMacroCall>? = null

    fun getMacroCall(): RsMacroCall? {
        val ref = cachedMacroCall?.get()
        return ref?.takeIf { it.isValid }
    }

    fun isUpToDate(call: RsMacroCall, def: RsMacro?): Boolean =
        callHash == call.bodyHash && def?.bodyHash == defHash

    fun bindAndUpdateStubIndex(call: RsMacroCall) {
        cachedMacroCall = SoftReference(call)
        stubIndex = calcStubIndex(call)
    }

    fun bindTo(call: RsMacroCall) {
        cachedMacroCall = SoftReference(call)
    }

    fun getExpansion(): MacroExpansion? {
        checkReadAccessAllowed()
        val psi = getExpansionPsi() ?: return null // expanded erroneous
        return getExpansionFromExpandedFile(MacroExpansionContext.ITEM, psi)
    }

    private fun getExpansionPsi(): RsFile? {
        val expansionFile = expansionFile?.takeIf { it.isValid } ?: return null
        check(expansionFile.fileType == RsFileType)
        val psi = PsiManager.getInstance(sourceFile.project).findFile(expansionFile) as? RsFile ?: return null
        return psi.takeIf { it.isValid }
    }

    fun makePipeline(): Pipeline.Stage1ResolveAndExpand {
        val call = getMacroCall()
        return if (call != null) ExpansionPipeline.Stage1(call, this) else InvalidationPipeline.Stage1(this)
    }

    fun writeTo(data: DataOutputStream) {
        data.apply {
            writeUTFNullable(expansionFileUrl)
            writeHashCodeNullable(defHash)
            writeHashCodeNullable(callHash)
            writeInt(stubIndex)
        }
    }
}

private data class SerializedExpandedMacroInfo(
    val expansionFileUrl: String?,
    val callHash: HashCode?,
    val defHash: HashCode?,
    val stubIndex: Int
) {
    fun toExpandedMacroInfo(sourceFile: SourceFile): ExpandedMacroInfo? {
        val file = expansionFileUrl?.let { VirtualFileManager.getInstance().findFileByUrl(it) }
        return ExpandedMacroInfo(
            sourceFile,
            file,
            callHash,
            defHash,
            stubIndex
        )
    }

    companion object {
        fun readFrom(data: DataInputStream): SerializedExpandedMacroInfo {
            return SerializedExpandedMacroInfo(
                data.readUTFNullable(),
                data.readHashCodeNullable(),
                data.readHashCodeNullable(),
                data.readInt()
            )
        }
    }
}

private fun DataOutputStream.writeUTFNullable(str: String?) {
    if (str == null) {
        writeBoolean(false)
    } else {
        writeBoolean(true)
        writeUTF(str)
    }
}

private fun DataInputStream.readUTFNullable(): String? {
    return if (readBoolean()) {
        readUTF()
    } else {
        null
    }
}

private fun DataOutputStream.writeHashCode(hash: HashCode) =
    write(hash.toByteArray())

private fun DataInputStream.readHashCode(): HashCode {
    val bytes = ByteArray(HashCode.ARRAY_LEN)
    readFully(bytes)
    return HashCode.fromByteArray(bytes)
}

private fun DataOutputStream.writeHashCodeNullable(hash: HashCode?) {
    if (hash == null) {
        writeBoolean(false)
    } else {
        writeBoolean(true)
        writeHashCode(hash)
    }
}

private fun DataInputStream.readHashCodeNullable(): HashCode? {
    return if (readBoolean()) {
        readHashCode()
    } else {
        null
    }
}

private fun calcStubIndex(psi: StubBasedPsiElement<*>): Int {
    val index = PsiAnchor.calcStubIndex(psi)
    if (index == -1) {
        error("Failed to calc stub index for element: $psi")
    }
    return index
}

private val VirtualFile.fileId: Int
    get() = (this as VirtualFileWithId).id
