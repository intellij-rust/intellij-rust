/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.macros

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.ModificationTracker
import com.intellij.openapi.util.SimpleModificationTracker
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.VirtualFileWithId
import com.intellij.openapi.vfs.newvfs.FileAttribute
import com.intellij.psi.PsiAnchor
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiManager
import com.intellij.psi.StubBasedPsiElement
import com.intellij.psi.impl.source.StubbedSpine
import com.intellij.util.indexing.FileBasedIndexScanRunnableCollector
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
import java.io.IOException
import java.lang.ref.WeakReference
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

class ExpandedMacroStorage(val project: Project) {
    // Data structures are guarded by the platform RWLock
    private val sourceFiles: TIntObjectHashMap<SourceFile> = TIntObjectHashMap()
    private val expandedFileToInfo: TIntObjectHashMap<ExpandedMacroInfoImpl> = TIntObjectHashMap()
    private val stepped: Array<MutableList<SourceFile>?> = arrayOfNulls(DEFAULT_RECURSION_LIMIT)
    private val _modificationTracker: SimpleModificationTracker = SimpleModificationTracker()

    val isEmpty: Boolean get() = sourceFiles.isEmpty
    val modificationTracker: ModificationTracker get() = _modificationTracker

    fun deserialize(sfs: List<SourceFile>) {
        for (sf in sfs) {
            sourceFiles.put(sf.fileId, sf)
            getOrPutStagedList(sf.depthNotSync).add(sf)
            sf.forEachInfoNotSync { if (it.fileId > 0) expandedFileToInfo.put(it.fileId, it) }
        }
    }

    fun clear() {
        checkWriteAccessAllowed()
        _modificationTracker.incModificationCount()
        sourceFiles.clear()
        expandedFileToInfo.clear()
        stepped.fill(null)
    }

    fun processExpandedMacroInfos(action: (ExpandedMacroInfo) -> Unit) {
        checkReadAccessAllowed()
        expandedFileToInfo.forEachValue { action(it); true }
    }

    private fun getOrPutStagedList(sf: SourceFile): MutableList<SourceFile> {
        return getOrPutStagedList(sf.depth)
    }

    private fun getOrPutStagedList(step: Int): MutableList<SourceFile> {
        var list = stepped[step]
        if (list == null) {
            list = mutableListOf()
            stepped[step] = list
        }
        return list
    }

    fun makeExpansionTask(map: Map<VirtualFile, List<RsMacroCall>>): Sequence<List<Extractable>> {
        val v2 = WriteAction.computeAndWait<MutableMap<SourceFile, List<RsMacroCall>>, Throwable> {
            map.mapKeysTo(HashMap<SourceFile, List<RsMacroCall>>()) { (file, _) ->
                getOrCreateSourceFile(file) ?: error("Non-root source files are not supported")
            }
        }
        return runReadActionInSmartMode(project) {
            makeValidationTask(false, v2)
        }
    }

    fun makeValidationTask(
        workspaceOnly: Boolean,
        map: MutableMap<SourceFile, List<RsMacroCall>> = mutableMapOf()
    ): Sequence<List<Extractable>> {
        checkReadAccessAllowed()

        return stepped.asSequence().withIndex().map { (i, step) ->
            // Release memory. [map] contains prefetched calls only for the first step, so clear it on the second step
            if (i == 1) map.clear()
            step?.map { sf -> Extractable(sf, workspaceOnly, map[sf]) }.orEmpty()
        }
    }

    fun moveSourceFilesToStep(files: Collection<SourceFile>, step: Int) {
        checkWriteAccessAllowed()
        val groupedByStep = files.groupBy { it.depth }

        for ((currentStep, group) in groupedByStep) {
            stepped[currentStep]?.removeAll(group.toHashSet())
            group.forEach {
                it.depth = step
            }
            getOrPutStagedList(step).addAll(group)
        }
    }

    fun addExpandedMacro(
        oldInfo: ExpandedMacroInfo,
        callHash: HashCode?,
        defHash: HashCode?,
        expansionFile: VirtualFile?,
        ranges: RangeMap?
    ): ExpandedMacroInfoImpl {
        checkWriteAccessAllowed()
        _modificationTracker.incModificationCount()
        @Suppress("NAME_SHADOWING")
        val oldInfo = oldInfo as ExpandedMacroInfoImpl

        val sourceFile = oldInfo.sourceFile
        val newInfo = ExpandedMacroInfoImpl(
            sourceFile,
            expansionFile,
            defHash,
            callHash,
            oldInfo.macroCallStubIndex,
            oldInfo.macroCallStrongRef
        )

        sourceFile.replaceInfo(oldInfo, newInfo)

        if (oldInfo.expansionFile != newInfo.expansionFile && oldInfo.fileId > 0) {
            expandedFileToInfo.remove(oldInfo.fileId)
        }
        if (newInfo.fileId > 0) {
            expandedFileToInfo.put(newInfo.fileId, newInfo)
        }

        newInfo.expansionFile?.let { getOrCreateSourceFile(it) }

        if (newInfo.expansionFile != null && ranges != null) {
            newInfo.expansionFile.writeRangeMap(ranges)
        }

        return newInfo
    }

    fun removeInvalidInfo(oldInfo: ExpandedMacroInfo, clean: Boolean) {
        checkWriteAccessAllowed()
        _modificationTracker.incModificationCount()
        @Suppress("NAME_SHADOWING")
        val oldInfo = oldInfo as ExpandedMacroInfoImpl

        expandedFileToInfo.remove(oldInfo.fileId)
        val sf = oldInfo.sourceFile
        sf.removeInfo(oldInfo)
        if (clean) {
            removeSourceFileIfEmpty(sf)
        }
    }

    fun removeSourceFileIfEmpty(sf: SourceFile) {
        checkWriteAccessAllowed()
        if (sf.isEmpty()) {
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

    companion object {
        private val LOG = Logger.getInstance(ExpandedMacroStorage::class.java)

        fun saveStorage(storage: ExpandedMacroStorage, data: DataOutputStream) {
            data.writeInt(STORAGE_VERSION)
            data.writeInt(MacroExpander.EXPANDER_VERSION)
            data.writeInt(RsFileStub.Type.stubVersion)
            data.writeInt(RANGE_MAP_ATTRIBUTE_VERSION)

            data.writeInt(storage.sourceFiles.size())
            storage.sourceFiles.forEachValue { it.writeTo(data); true }
        }
    }
}

private const val STORAGE_VERSION = 10
private const val RANGE_MAP_ATTRIBUTE_VERSION = 2

class SerializedExpandedMacroStorage private constructor(
    private val serSourceFiles: List<SerializedSourceFile>
) {
    fun deserializeInReadAction(project: Project): ExpandedMacroStorage {
        checkReadAccessAllowed()
        val storage = ExpandedMacroStorage(project)
        val sourceFiles = serSourceFiles.mapNotNull { it.toSourceFile(storage) }
        storage.deserialize(sourceFiles)
        return storage
    }

    companion object {

        @Throws(IOException::class)
        fun load(data: DataInputStream): SerializedExpandedMacroStorage? {
            if (data.readInt() != STORAGE_VERSION) return null
            if (data.readInt() != MacroExpander.EXPANDER_VERSION) return null
            if (data.readInt() != RsFileStub.Type.stubVersion) return null
            if (data.readInt() != RANGE_MAP_ATTRIBUTE_VERSION) return null

            val sourceFilesSize = data.readInt()
            val serSourceFiles = ArrayList<SerializedSourceFile>(sourceFilesSize)

            for (i in 0 until sourceFilesSize) {
                serSourceFiles += SerializedSourceFile.readFrom(data)
            }
            return SerializedExpandedMacroStorage(serSourceFiles)
        }
    }
}

/**
 * A [SourceFile] object is associated to any Rust file that contains macro calls. The main purpose
 * of [SourceFile] is to map between macro calls and expansions ([RsMacroCall] <-> [ExpandedMacroInfoImpl]).
 *
 * There are 3 basic mechanisms to keep mapping between macro call and expansion:
 * - **Via stub ids**. Each stub has an index (aka *id*, incremental integer given in DFS order; see
 *   [calcStubIndex]). A PSI element can be retrieved by such index if the PSI file has not been changed
 *   since receipt of the stub index. We check that PSI file is not changed by the modification stamp,
 *   see [getActualModStamp]
 * - **Via strong PSI refs**. Before any PSI modification, a strong ref to each known [RsMacroCall] in
 *   the file is stored (see [ExpandedMacroInfoImpl.macroCallStrongRef]). Then, after the end of PSI
 *   modification, stub indices are restored from the stored refs. See [switchToStrongRefsTemporary]
 * - **Hash-based recover**. If the refs are lost, they can be recovered. Each [ExpandedMacroInfoImpl] stores
 *   hashes of a macro call and a macro definition. They are mostly used to check whether a macro should be
 *   re-expanded (if the hash of the body/definition is changed) but can be also used to recover
 *   links to a PSI. See [recoverRefs]. References may be lost when modifying stub-based PSI.
 *
 * # Threading notes:
 * There are some mutable fields:
 * - [modificationStamp]
 * - [infos] (list content)
 * - [ExpandedMacroInfoImpl.macroCallStubIndex]
 * - [ExpandedMacroInfoImpl.macroCallStrongRef]
 *
 * They:
 * - can be accessed (read or mutated) in the write action without any additional synchronization
 * - can be accessed (read or mutated) in the read action under the [lock]
 * - cannot be accessed outside of read/write action
 *
 * These invariants are checked by [assertSync] method.
 */
class SourceFile(
    val storage: ExpandedMacroStorage,
    val file: VirtualFile,
    private var _depth: Int,
    private var _modificationStamp: Long = FRESH_FLAG,
    private val _infos: MutableList<ExpandedMacroInfoImpl> = mutableListOf()
) {
    private val lock = ReentrantLock()

    private val fileUrl: String get() = file.url
    val fileId: Int get() = file.fileId
    val project: Project get() = storage.project

    // Used only for deserialization
    val depthNotSync: Int get() = _depth

    var depth: Int
        get() {
            assertSync()
            return _depth
        }
        set(value) {
            assertSync()
            _depth = value
        }

    private var modificationStamp: Long
        get() {
            assertSync()
            return _modificationStamp
        }
        set(value) {
            assertSync()
            _modificationStamp = value
        }

    private val infos: MutableList<ExpandedMacroInfoImpl>
        get() {
            // We really should check access to [_infos] contents instead of [_infos] itself
            assertSync()
            return _infos
        }

    private val rootSourceFile: SourceFile by lazy(LazyThreadSafetyMode.PUBLICATION) {
        findRootSourceFile()
    }

    // Should not be lazy b/c target/pkg/origin can be changed
    private val isBelongToWorkspace: Boolean
        get() = rootSourceFile.loadPsi()?.containingCargoTarget?.pkg?.origin == PackageOrigin.WORKSPACE

    /**
     * Checks that [modificationStamp], [infos] content, [ExpandedMacroInfoImpl.macroCallStrongRef] or
     * [ExpandedMacroInfoImpl.macroCallStubIndex] can be accessed. See docs for [SourceFile] for more info
     */
    private fun assertSync() {
        val app = ApplicationManager.getApplication()
        check(app.isWriteAccessAllowed || lock.isHeldByCurrentThread && app.isReadAccessAllowed)
    }

    private inline fun <T> sync(action: () -> T): T =
        lock.withLock(action)

    private fun switchToStubRefsInReadAction(
        kind: RefKind,
        action: (MutableList<ExpandedMacroInfoImpl>) -> Unit
    ) {
        checkReadAccessAllowed()
        sync {
            if (getRefKind() != kind) return
            ProgressManager.getInstance().executeNonCancelableSection {
                action(infos)
                modificationStamp = getActualModStamp()
            }
        }
    }

    private inline fun switchRefsInWriteAction(action: (MutableList<ExpandedMacroInfoImpl>) -> Long) {
        // No synchronization needed in the write action
        checkWriteAccessAllowed()
        modificationStamp = action(infos)
    }

    private inline fun <T> syncAndMapInfos(mapper: (ExpandedMacroInfoImpl) -> T): List<T> = sync {
        checkReadAccessAllowed()
        return infos.map(mapper)
    }

    // Used only during deserialization
    fun forEachInfoNotSync(action: (ExpandedMacroInfoImpl) -> Unit) {
        _infos.forEach(action)
    }

    fun isEmpty(): Boolean = sync {
        infos.isEmpty()
    }

    private enum class RefKind {
        /** The [file] is not valid (e.g. is deleted) */
        INVALID,
        /** No refs created yet */
        FRESH,
        /** Uses [ExpandedMacroInfoImpl.macroCallStrongRef] */
        STRONG,
        /** Uses [ExpandedMacroInfoImpl.macroCallStubIndex] */
        STUB,
        /** Refs was lost and should be recovered with [recoverRefs] */
        LOST
    }

    private fun getRefKind(): RefKind {
        assertSync()
        if (!file.isValid || project.isDisposed) return RefKind.INVALID

        return when (modificationStamp) {
            STRONG_REFS_FLAG -> RefKind.STRONG
            FORCE_RELINK_FLAG -> RefKind.LOST
            FRESH_FLAG -> RefKind.FRESH
            getActualModStamp() -> RefKind.STUB
            else -> if (infos.isEmpty()) RefKind.FRESH else RefKind.LOST
        }
    }

    private interface RefMatcher<T> {
        val allowFreshRebind: Boolean get() = false
        fun none(): T
        fun strong(infos: List<ExpandedMacroInfoImpl>): T
        fun stub(psi: RsFile, infos: List<ExpandedMacroInfoImpl>): T
    }

    private fun <T> matchRefs(
        matcher: RefMatcher<T>,
        prefetchedCalls: List<RsMacroCall>? = null
    ): T {
        checkReadAccessAllowed()
        val psi = loadPsi() ?: return matcher.none()
        val kind = sync {
            val kind = getRefKind()
            // `STRONG -> STUB` transition is allowed in the read action, so this must
            // be called in the same `sync` section where `getRefKind` called
            if (kind == RefKind.STRONG) return matcher.strong(infos)
            kind
        }
        return when (kind) {
            RefKind.INVALID -> matcher.none()
            RefKind.FRESH -> {
                if (!matcher.allowFreshRebind) return matcher.none()
                // Only `FRESH -> STUB` transition is allowed in the read action, so synchronization
                // isn't needed - multiple concurrent invocations will result to the same values
                freshExtractMacros(prefetchedCalls)
                matcher.stub(psi, _infos)
            }
            RefKind.STRONG -> error("unreachable") // handled above
            RefKind.STUB -> {
                Testmarks.stubBasedRefMatch.hit()
                // Transition to any state from `STUB` is forbidden in the read action
                matcher.stub(psi, _infos)
            }
            RefKind.LOST -> {
                // Only `LOST -> STUB` transition allowed in the read action, so synchronization
                // isn't needed - multiple concurrent invocations will result to the same values
                recoverRefs(prefetchedCalls)
                matcher.stub(psi, _infos)
            }
        }
    }

    fun replaceInfo(oldInfo: ExpandedMacroInfoImpl, newInfo: ExpandedMacroInfoImpl) {
        checkWriteAccessAllowed()
        val infos = infos // No synchronization needed in the write action
        val oldInfoIndex = infos.indexOf(oldInfo)
        if (oldInfoIndex != -1) {
            infos[oldInfoIndex] = newInfo
        } else {
            infos.add(newInfo)
        }
    }

    fun removeInfo(info: ExpandedMacroInfoImpl) {
        checkWriteAccessAllowed()
        infos.remove(info) // No synchronization needed in the write action
    }

    fun extract(workspaceOnly: Boolean, calls: List<RsMacroCall>?): List<Pipeline.Stage1ResolveAndExpand>? {
        if (workspaceOnly && !isBelongToWorkspace) {
            // very hot path; the condition should be as fast as possible
            return emptyList()
        }

        return extract(calls)
    }

    private fun extract(calls: List<RsMacroCall>?): List<Pipeline.Stage1ResolveAndExpand>? {
        checkReadAccessAllowed() // Needed to access PSI
        checkIsSmartMode(project)

        val isExpansionFile = project.macroExpansionManager.isExpansionFile(file)
        val isIndexedFile = file.isValid && (
            FileBasedIndexScanRunnableCollector.getInstance(project).shouldCollect(file) || isExpansionFile)

        val stages1 = if (!isIndexedFile) {
            // The file is now outside of the project, so we should not access it.
            // All infos of this file should be invalidated
            syncAndMapInfos { info ->
                info.markInvalid()
                info.makeInvalidationPipeline()
            }
        } else {
            // If project file doesn't have crate root
            // we shouldn't try to expand its macro calls
            if (!isExpansionFile) {
                val psiFile = loadPsi()
                if (psiFile != null && psiFile.crateRoot == null) return null
            }
            matchRefs(MakePipeline(), calls)
        }
        return stages1.takeIf { it.isNotEmpty() } ?: listOf(RemoveSourceFileIfEmptyPipeline(this))
    }

    private inner class MakePipeline : RefMatcher<List<Pipeline.Stage1ResolveAndExpand>> {
        override val allowFreshRebind: Boolean get() = true

        override fun none(): List<Pipeline.Stage1ResolveAndExpand> =
            syncAndMapInfos { it.makeInvalidationPipeline() }

        override fun strong(infos: List<ExpandedMacroInfoImpl>): List<Pipeline.Stage1ResolveAndExpand> =
            infos.map { it.makePipeline(it.derefMacroCall()) }

        override fun stub(psi: RsFile, infos: List<ExpandedMacroInfoImpl>): List<Pipeline.Stage1ResolveAndExpand> {
            return infos.map { info ->
                val stubIndex = info.macroCallStubIndex
                val call = if (stubIndex != -1) {
                    psi.stubbedSpine.getMacroCall(stubIndex) ?: return emptyList()
                } else {
                    null
                }
                info.makePipeline(call)
            }
        }
    }

    private fun freshExtractMacros(prefetchedCalls: List<RsMacroCall>?) {
        val psi = loadPsi() ?: return
        val calls = prefetchedCalls ?: psi.stubDescendantsOfTypeStrict<RsMacroCall>().filter { it.isTopLevelExpansion }
        val newInfos = calls.map { call ->
            ExpandedMacroInfoImpl(this, null, null, call.bodyHash, call.calcStubIndex())
        }
        switchToStubRefsInReadAction(RefKind.FRESH) { infos ->
            check(infos.isEmpty())
            infos.addAll(newInfos)
        }
    }

    /**
     * Threading notes:
     * We don't want to execute the entire method in synchronized section because it performs macro name
     * resolution (heavy stuff). All changes are published in the last [switchToStubRefsInReadAction] call
     */
    private fun recoverRefs(prefetchedCalls: List<RsMacroCall>?) {
        checkReadAccessAllowed()
        Testmarks.refsRecover.hit()

        val psi = loadPsi() ?: return
        val calls = prefetchedCalls ?: psi.stubDescendantsOfTypeStrict<RsMacroCall>().filter { it.isTopLevelExpansion }
        val unboundInfos = sync { infos.toMutableList() }
        val orphans = mutableListOf<RsMacroCall>()
        val bindList = mutableListOf<Pair<ExpandedMacroInfoImpl, Int>>()
        for (call in calls) {
            val info = unboundInfos.find { it.isUpToDate(call, call.resolveToMacro()) }
            if (info != null) {
                Testmarks.refsRecoverExactHit.hit()
                unboundInfos.remove(info)
                bindList += Pair(info, call.calcStubIndex())
            } else {
                orphans += call
            }
        }

        val infosToAdd = mutableListOf<ExpandedMacroInfoImpl>()

        for (call in orphans) {
            val info = unboundInfos.find { it.callHash == call.bodyHash }
            if (info != null) {
                Testmarks.refsRecoverCallHit.hit()
                unboundInfos.remove(info)
                bindList += Pair(info, call.calcStubIndex())
            } else {
                Testmarks.refsRecoverNotHit.hit()
                infosToAdd += ExpandedMacroInfoImpl(this, null, null, call.bodyHash, call.calcStubIndex())
            }
        }

        switchToStubRefsInReadAction(RefKind.LOST) { infos ->
            for ((info, callStubIndex) in bindList) {
                info.macroCallStrongRef = null
                info.macroCallStubIndex = callStubIndex
            }
            unboundInfos.forEach {
                it.markInvalid()
            }
            infos.addAll(infosToAdd)
        }
    }

    private fun areStubIndicesActual(): Boolean {
        val modificationStamp = modificationStamp
        return modificationStamp >= 0 && modificationStamp == getActualModStamp()
    }

    private fun getActualModStamp(): Long =
        loadPsi()?.viewProvider?.modificationStamp ?: FORCE_RELINK_FLAG

    private fun loadPsi(): RsFile? =
        file.takeIf { it.isValid }?.toPsiFile(project) as? RsFile

    fun getInfoForCall(seekingCall: RsMacroCall): ExpandedMacroInfo? {
        testAssert { seekingCall.containingFile.virtualFile == file }

        return matchRefs(object : RefMatcher<ExpandedMacroInfoImpl?> {
            override fun none(): ExpandedMacroInfoImpl? = null

            override fun strong(infos: List<ExpandedMacroInfoImpl>): ExpandedMacroInfoImpl? =
                infos.find { it.macroCallStrongRef == seekingCall }

            override fun stub(psi: RsFile, infos: List<ExpandedMacroInfoImpl>): ExpandedMacroInfoImpl? {
                Testmarks.stubBasedLookup.hit()
                val seekingStubIndex = seekingCall.calcStubIndex()
                return infos.find { it.macroCallStubIndex == seekingStubIndex }
            }
        })
    }

    fun getCallForInfo(info: ExpandedMacroInfoImpl): RsMacroCall? {
        check(info.sourceFile == this)

        return matchRefs(object : RefMatcher<RsMacroCall?> {
            override fun none(): RsMacroCall? = null

            override fun strong(infos: List<ExpandedMacroInfoImpl>): RsMacroCall? =
                info.derefMacroCall()

            override fun stub(psi: RsFile, infos: List<ExpandedMacroInfoImpl>): RsMacroCall? {
                val stubIndex = info.macroCallStubIndex
                if (stubIndex == -1) return null
                return psi.stubbedSpine.getMacroCall(stubIndex)
            }
        })
    }

    fun newMacroCallsAdded(newMacroCalls: Collection<RsMacroCall>) {
        checkWriteAccessAllowed()
        val refKind = getRefKind()
        if (refKind == RefKind.STRONG) {
            val infos = infos // No synchronization needed in the write action
            for (call in newMacroCalls) {
                if (call.isTopLevelExpansion && infos.none { it.macroCallStrongRef == call }) {
                    infos += ExpandedMacroInfoImpl(this, null, null, null, macroCallStrongRef = call)
                }
            }
        } else if (refKind != RefKind.FRESH) {
            switchRefsInWriteAction { FORCE_RELINK_FLAG }
        }
    }

    fun switchToStrongRefsTemporary() {
        if (switchToStrongRefs()) {
            PsiDocumentManager.getInstance(project).performLaterWhenAllCommitted {
                releaseStrongRefs()
            }
        }
    }

    private fun switchToStrongRefs(): Boolean {
        checkWriteAccessAllowed()
        if (getRefKind() == RefKind.STUB) {
            val psi = loadPsi() ?: return false
            // Used instead of `psi.stubbedSpine` to avoid switching to spine refs during PSI event processing
            // which is forbidden (see `PsiFileImpl.subtreeChanged`)
            val spine = psi.greenStubTree?.spine
                ?: psi.treeElement?.stubbedSpine
                ?: return false

            switchRefsInWriteAction { infos ->
                for (info in infos) {
                    val stubIndex = info.macroCallStubIndex
                    if (stubIndex != -1) {
                        info.macroCallStrongRef = spine.getMacroCall(stubIndex)
                            ?: return@switchRefsInWriteAction FORCE_RELINK_FLAG
                    }
                }
                STRONG_REFS_FLAG
            }
            return true
        }
        return false
    }

    private fun releaseStrongRefs() {
        switchToStubRefsInReadAction(RefKind.STRONG) { infos ->
            val areStubIndicesActual = areStubIndicesActual()
            for (info in infos) {
                if (!areStubIndicesActual) {
                    info.macroCallStubIndex = info.derefMacroCall()?.calcStubIndex() ?: -1
                }
                info.macroCallStrongRef = null
            }
        }
    }

    private fun StubbedSpine.getMacroCall(stubIndex: Int): RsMacroCall? {
        val element = getStubPsi(stubIndex)
        return if (element is RsMacroCall) {
            element
        } else {
            val document = FileDocumentManager.getInstance().getCachedDocument(file)
            val lastCommittedDocStamp =
                document?.let { PsiDocumentManager.getInstance(project).getLastCommittedStamp(it) }
            MACRO_LOG.error(
                "Detected broken stub reference to a macro!",
                Throwable(),
                "File: `${file.path}`,",
                "Stored stamp: ${sync { modificationStamp }},",
                "Actual stamp: ${getActualModStamp()},",
                "PSI stamp: ${loadPsi()?.viewProvider?.modificationStamp},",
                "File stamp: ${file.modificationStamp},",
                "Document stamp: ${document?.modificationStamp},",
                "Last committed document stamp: $lastCommittedDocStamp,",
                "Stub index: $stubIndex,",
                "Found element: $element, ",
                "Found element text: `${element?.text}`"
            )
            markForRebind()
            null
        }
    }

    fun markForRebind() = sync {
        if (modificationStamp != FRESH_FLAG) modificationStamp = FORCE_RELINK_FLAG
    }

    private fun ExpandedMacroInfoImpl.derefMacroCall(): RsMacroCall? {
        assertSync()
        return macroCallStrongRef?.takeIf { it.isValid }
    }

    private fun ExpandedMacroInfoImpl.markInvalid() {
        assertSync()
        macroCallStrongRef = null
        macroCallStubIndex = -1
    }

    fun writeTo(data: DataOutputStream): Unit = sync {
        data.apply {
            writeUTF(fileUrl)
            writeInt(depth)
            writeLong(modificationStamp)
            writeInt(infos.size)
            infos.forEach { it.writeTo(data) }
        }
    }
}

private const val FORCE_RELINK_FLAG: Long = -1L
private const val STRONG_REFS_FLAG: Long = -2L
private const val FRESH_FLAG: Long = -3L

private data class SerializedSourceFile(
    val fileUrl: String,
    val depth: Int,
    private var modificationStamp: Long,
    private val serInfos: List<SerializedExpandedMacroInfo>
) {
    fun toSourceFile(storage: ExpandedMacroStorage): SourceFile? {
        checkReadAccessAllowed() // Needed to access VFS

        val file = VirtualFileManager.getInstance().findFileByUrl(fileUrl) ?: return null
        val infos = ArrayList<ExpandedMacroInfoImpl>(serInfos.size)
        val sf = SourceFile(
            storage,
            file,
            depth,
            modificationStamp,
            infos
        )

        serInfos.mapNotNullTo(infos) { it.toExpandedMacroInfo(sf) }
        if (infos.size != serInfos.size) {
            sf.markForRebind()
        }
        return sf
    }

    companion object {
        fun readFrom(data: DataInputStream): SerializedSourceFile {
            val fileUrl = data.readUTF()
            val depth = data.readInt()
            val modificationStamp = data.readLong()
            val infosSize = data.readInt()
            val serInfos = (0 until infosSize).map { SerializedExpandedMacroInfo.readFrom(data) }

            return SerializedSourceFile(
                fileUrl,
                depth,
                modificationStamp,
                serInfos
            )
        }
    }
}

private tailrec fun SourceFile.findRootSourceFile(): SourceFile {
    val file = file.takeIf { it.isValid } ?: return this
    val parentSF = storage.getInfoForExpandedFile(file)?.sourceFile ?: return this
    return parentSF.findRootSourceFile()
}

interface ExpandedMacroInfo {
    val sourceFile: SourceFile
    val expansionFile: VirtualFile?
    fun getMacroCall(): RsMacroCall?
    fun isUpToDate(call: RsMacroCall, def: RsMacro?): Boolean
    fun getExpansion(): MacroExpansion?
}

class ExpandedMacroInfoImpl(
    override val sourceFile: SourceFile,
    override val expansionFile: VirtualFile?,
    private val defHash: HashCode?,
    val callHash: HashCode?,
    var macroCallStubIndex: Int = -1,
    var macroCallStrongRef: RsMacroCall? = null
) : ExpandedMacroInfo {
    private val expansionFileUrl: String? get() = expansionFile?.url
    val fileId: Int get() = expansionFile?.fileId ?: -1

    override fun getMacroCall(): RsMacroCall? =
        sourceFile.getCallForInfo(this)

    override fun isUpToDate(call: RsMacroCall, def: RsMacro?): Boolean =
        callHash == call.bodyHash && def?.bodyHash == defHash

    override fun getExpansion(): MacroExpansion? {
        checkReadAccessAllowed()
        val psi = getExpansionPsi() ?: return null // expanded erroneous
        return getExpansionFromExpandedFile(MacroExpansionContext.ITEM, psi)
    }

    private fun getExpansionPsi(): RsFile? {
        val expansionFile = expansionFile?.takeIf { it.isValid } ?: return null
        testAssert { expansionFile.fileType == RsFileType }
        return PsiManager.getInstance(sourceFile.project).findFile(expansionFile) as? RsFile
    }

    fun makePipeline(call: RsMacroCall?): Pipeline.Stage1ResolveAndExpand {
        return if (call != null) ExpansionPipeline.Stage1(call, this) else InvalidationPipeline.Stage1(this)
    }

    fun makeInvalidationPipeline(): Pipeline.Stage1ResolveAndExpand =
        makePipeline(null)

    fun writeTo(data: DataOutputStream) {
        data.apply {
            writeUTFNullable(expansionFileUrl)
            writeHashCodeNullable(defHash)
            writeHashCodeNullable(callHash)
            writeInt(macroCallStubIndex)
        }
    }
}

private data class SerializedExpandedMacroInfo(
    val expansionFileUrl: String?,
    val callHash: HashCode?,
    val defHash: HashCode?,
    val stubIndex: Int
) {
    fun toExpandedMacroInfo(sourceFile: SourceFile): ExpandedMacroInfoImpl? {
        val file = if (expansionFileUrl != null) {
            VirtualFileManager.getInstance().findFileByUrl(expansionFileUrl) ?: return null
        } else {
            null
        }
        return ExpandedMacroInfoImpl(
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

private fun StubBasedPsiElement<*>.calcStubIndex(): Int {
    ProgressManager.checkCanceled()
    val index = PsiAnchor.calcStubIndex(this)
    if (index == -1) {
        val shouldCreateStub = elementType.shouldCreateStub(node)
        error("Failed to calc stub index for the element: ${this}, shouldCreateStub: $shouldCreateStub")
    }
    return index
}

private val VirtualFile.fileId: Int
    get() = (this as VirtualFileWithId).id

/** We use [WeakReference] because uncached [loadRangeMap] is quite cheap */
private val MACRO_RANGE_MAP_CACHE_KEY: Key<WeakReference<RangeMap>> = Key.create("MACRO_RANGE_MAP_CACHE_KEY")
private val RANGE_MAP_ATTRIBUTE = FileAttribute(
    "org.rust.macro.RangeMap",
    RANGE_MAP_ATTRIBUTE_VERSION,
    /*fixedSize = */ true // don't allocate extra space for each record
)

private fun VirtualFile.writeRangeMap(ranges: RangeMap) {
    checkWriteAccessAllowed()

    RANGE_MAP_ATTRIBUTE.writeAttribute(this).use {
        ranges.writeTo(it)
    }

    if (getUserData(MACRO_RANGE_MAP_CACHE_KEY)?.get() != null) {
        putUserData(MACRO_RANGE_MAP_CACHE_KEY, WeakReference(ranges))
    }
}

fun VirtualFile.loadRangeMap(): RangeMap? {
    checkReadAccessAllowed()

    getUserData(MACRO_RANGE_MAP_CACHE_KEY)?.get()?.let { return it }

    val data = RANGE_MAP_ATTRIBUTE.readAttribute(this) ?: return null
    val ranges = RangeMap.readFrom(data)
    putUserData(MACRO_RANGE_MAP_CACHE_KEY, WeakReference(ranges))
    return ranges
}
