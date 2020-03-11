/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.macros

import com.intellij.openapi.application.AccessToken
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.progress.util.AbstractProgressIndicatorExBase
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.SimpleModificationTracker
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.ex.ProgressIndicatorEx
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.util.containers.ContainerUtil
import com.intellij.util.io.storage.HeavyProcessLatch
import org.rust.lang.core.psi.RsMacroCall
import org.rust.lang.core.psi.RsMembers
import org.rust.lang.core.psi.ext.RsMod
import org.rust.lang.core.psi.ext.bodyHash
import org.rust.lang.core.psi.ext.macroBody
import org.rust.lang.core.psi.ext.resolveToMacro
import org.rust.lang.core.resolve.DEFAULT_RECURSION_LIMIT
import org.rust.lang.core.resolve.ref.RsMacroPathReferenceImpl
import org.rust.lang.core.resolve.ref.RsResolveCache
import org.rust.openapiext.*
import org.rust.stdext.HashCode
import org.rust.stdext.executeSequentially
import org.rust.stdext.nextOrNull
import org.rust.stdext.supplyAsync
import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.max
import kotlin.streams.toList
import kotlin.system.measureNanoTime

abstract class MacroExpansionTaskBase(
    project: Project,
    private val storage: ExpandedMacroStorage,
    private val pool: Executor,
    private val vfsBatchFactory: () -> MacroExpansionVfsBatch,
    private val createExpandedSearchScope: (Int) -> GlobalSearchScope,
    private val stepModificationTracker: SimpleModificationTracker
) : Task.Backgroundable(project, "Expanding Rust macros", /* canBeCancelled = */ false) {
    private val transactionExecutor = TransactionExecutor(project)
    private val expander = MacroExpander(project)
    private val sync = CountDownLatch(1)
    private val estimateStages = AtomicInteger()
    private val doneStages = AtomicInteger()
    private val currentStep = AtomicInteger(-1)
    private val totalExpanded = AtomicInteger()
    private lateinit var realTaskIndicator: ProgressIndicator
    private lateinit var subTaskIndicator: ProgressIndicator
    private lateinit var expansionSteps: Iterator<List<Extractable>>
    @Volatile
    private var heavyProcessRequested = false

    @Volatile
    private var pendingFiles: List<Extractable> = emptyList()

    override fun run(indicator: ProgressIndicator) {
        checkReadAccessNotAllowed()
        indicator.checkCanceled()
        indicator.isIndeterminate = false
        realTaskIndicator = indicator

        expansionSteps = getMacrosToExpand().iterator()

        if (indicator is ProgressIndicatorEx) {
            // [indicator] can be an instance of [BackgroundableProcessIndicator] class, which is thread
            // sensitive and its `checkCanceled` method should be used only from a single thread
            // (see [ProgressWindow.MyDelegate.checkCanceled]). So we propagate cancellation.
            subTaskIndicator = EmptyProgressIndicator()
            indicator.addStateDelegate(object : AbstractProgressIndicatorExBase() {
                override fun cancel() = subTaskIndicator.cancel()
            })
        } else {
            subTaskIndicator = indicator
        }

        indicator.checkCanceled()
        var heavyProcessToken: AccessToken? = null
        try {
            submitExpansionTask()

            MACRO_LOG.debug("Awaiting")
            val duration = measureNanoTime {
                // 50ms - default progress bar update interval. See [ProgressDialog.UPDATE_INTERVAL]
                while (!sync.await(50, TimeUnit.MILLISECONDS)) {
                    indicator.fraction = calcProgress(
                        currentStep.get() + 1,
                        doneStages.get().toDouble() / max(estimateStages.get(), 1)
                    )

                    // If project is disposed, then queue will be disposed too, so we shouldn't await sub task finish
                    // (and sub task may not call `sync.countDown()`, so without this `break` we will be blocked
                    // forever)
                    if (project.isDisposed) break

                    // Enter heavy process mode only if at least one macro is not up-to-date
                    if (heavyProcessToken == null && heavyProcessRequested) {
                        heavyProcessToken = HeavyProcessLatch.INSTANCE.processStarted("Expanding Rust macros")
                    }
                }
            }
            MACRO_LOG.info("Task completed! ${totalExpanded.get()} total calls, millis: " + duration / 1_000_000)
        } finally {
            RsResolveCache.getInstance(project).endExpandingMacros()
            // Return all non expanded files to storage.
            // Otherwise, we will lose them until full re-expand
            movePendingFileToStep(pendingFiles, 0)
            heavyProcessToken?.let {
                it.finish()
                // Restart `DaemonCodeAnalyzer` after releasing `HeavyProcessLatch`. Used instead of
                // `DaemonCodeAnalyzer.restart()` to do restart more gracefully, i.e. don't invalidate
                // highlights if nothing actually changed
                WriteAction.runAndWait<Throwable> {  }
            }
        }
    }

    private fun calcProgress(step: Int, progress: Double): Double =
        (1 until step).map { stepProgress(it) }.sum() + stepProgress(step) * progress

    // It's impossible to know total quantity of macros, so we guess these values
    // (obtained empirically on some large projects)
    private fun stepProgress(step: Int): Double = when (step) {
        1 -> 0.3
        2 -> 0.2
        3 -> 0.1
        else -> 0.4 / (DEFAULT_RECURSION_LIMIT - 3)
    }

    private fun submitExpansionTask() {
        checkIsBackgroundThread()
        realTaskIndicator.text2 = "Waiting for index"
        val extractableList = executeUnderProgress(subTaskIndicator) {
            runReadActionInSmartMode(project) {
                var extractableList: List<Extractable>?
                do {
                    extractableList = expansionSteps.nextOrNull()
                    val step = currentStep.incrementAndGet()
                    MACRO_LOG.debug("Expansion step: $step")
                    stepModificationTracker.incModificationCount()
                } while (extractableList != null && extractableList.isEmpty())
                extractableList
            }
        }

        if (extractableList == null) {
            sync.countDown()
            return
        }

        realTaskIndicator.text = "Expanding Rust macros. Step " + (currentStep.get() + 1) + "/$DEFAULT_RECURSION_LIMIT"
        estimateStages.set(0)
        doneStages.set(0)

        val scope = createExpandedSearchScope(currentStep.get())
        val expansionState = MacroExpansionManager.ExpansionState(scope, stepModificationTracker)

        // All subsequent parallelStream tasks are executed on the same [pool]
        supplyAsync(pool) {
            realTaskIndicator.text2 = "Expanding macros"

            val pending = ContainerUtil.newConcurrentSet<Extractable>()
            val stages1 = (extractableList + pendingFiles).chunked(100).parallelStream().unordered().flatMap { extractable ->
                val result = executeUnderProgressWithWriteActionPriorityWithRetries(subTaskIndicator) {
                    // We need smart mode because rebind can be performed
                    runReadActionInSmartMode(project) {
                        project.macroExpansionManager.withExpansionState(expansionState) {
                            extractable.flatMap {
                                val extracted = it.extract()
                                if (extracted == null) {
                                    pending.add(it)
                                }
                                extracted.orEmpty()
                            }
                        }
                    }
                }
                estimateStages.addAndGet(result.size * Pipeline.STAGES)
                result.stream()
            }.toList()

            movePendingFileToStep(pending, currentStep.get())
            pendingFiles = pending.toList()
            MACRO_LOG.debug("Pending files: ${pendingFiles.size}")

            val stages2 = stages1.parallelStream().unordered().map { stage1 ->
                val result = executeUnderProgressWithWriteActionPriorityWithRetries(subTaskIndicator) {
                    // We need smart mode to resolve macros
                    runReadActionInSmartMode(project) {
                        project.macroExpansionManager.withExpansionState(expansionState) {
                            stage1.expand(project, expander)
                        }
                    }
                }
                doneStages.addAndGet(if (result is EmptyPipeline) Pipeline.STAGES else 1)

                // Enter heavy process mode only if at least one macros is not up-to-date
                if (result !is EmptyPipeline) {
                    heavyProcessRequested = true
                }
                result
            }.filter { it !is EmptyPipeline }.toList()

            realTaskIndicator.text2 = "Writing expansion results"

            if (stages2.isNotEmpty()) {
                // TODO support cancellation here
                //  Cancellation isn't supported because we should provide consistency between the filesystem and
                //  the storage, so if we created some files, we must add them to the storage, or they will be leaked.
                // We can cancel task if the project is disposed b/c after project reopen storage consistency will be
                // re-checked

                // Note that if project is disposed, this task will not be executed or may be executed partially
                executeSequentially(transactionExecutor, stages2.chunked(VFS_BATCH_SIZE)) { stages2c ->
                    runWriteAction {
                        val batch = vfsBatchFactory()
                        val stages3 = stages2c.map { stage2 ->
                            val result = stage2.writeExpansionToFs(batch, currentStep.get())
                            result
                        }
                        batch.applyToVfs()
                        for (stage3 in stages3) {
                            stage3.save(storage)
                        }
                        doneStages.addAndGet(stages3.size * 2)
                    }
                    totalExpanded.addAndGet(stages2c.size)
                    Unit
                }.thenApply { Unit }
            } else {
                CompletableFuture.completedFuture(Unit)
            }
        }.thenCompose { it }.handle { success: Unit?, t: Throwable? ->
            // This callback will be executed regardless of success or exceptional result
            if (success != null) {
                // Success
                if (ApplicationManager.getApplication().isDispatchThread) {
                    pool.execute(::runNextStep)
                } else {
                    runNextStep()
                }
            } else {
                when (val e = (t as? CompletionException)?.cause ?: t) {
                    null -> error("unreachable")
                    is ProcessCanceledException -> Unit // Task canceled
                    else -> {
                        // Error
                        MACRO_LOG.error("Error during macro expansion", e)
                    }
                }
                sync.countDown()
            }
            Unit
        }.exceptionally {
            // Handle exceptions that may be thrown by `handle` body
            sync.countDown()
            MACRO_LOG.error("Error during macro expansion", it)
            Unit
        }
    }

    private fun runNextStep() {
        try {
            submitExpansionTask()
        } catch (e: ProcessCanceledException) {
            sync.countDown()
        }
    }

    private fun movePendingFileToStep(newPendingFiles: Collection<Extractable>, step: Int) {
        val processedFiles = pendingFiles - newPendingFiles

        val duration = measureNanoTime {
            if (processedFiles.isEmpty()) return@measureNanoTime
            // TODO: split [processedFiles] into chunks to reduce UI freezes (if needed)
            WriteAction.runAndWait<Nothing> {
                storage.moveSourceFilesToStep(processedFiles.map { it.sf }, step)
            }
        }
        MACRO_LOG.debug("Moving of pending files to step $step: ${duration / 1000} μs")
    }

    protected abstract fun getMacrosToExpand(): Sequence<List<Extractable>>

    open fun canEat(other: MacroExpansionTaskBase): Boolean = false

    open val isProgressBarDelayed: Boolean get() = true

    companion object {
        /**
         * Higher values leads to better throughput (overall expansion time), but worse latency (UI freezes)
         */
        private const val VFS_BATCH_SIZE = 200
    }
}

class Extractable(val sf: SourceFile, private val workspaceOnly: Boolean, private val calls: List<RsMacroCall>?) {
    fun extract(): List<Pipeline.Stage1ResolveAndExpand>? {
        return sf.extract(workspaceOnly, calls)
    }
}

object Pipeline {
    const val STAGES: Int = 3

    interface Stage1ResolveAndExpand {
        /** must be a pure function */
        fun expand(project: Project, expander: MacroExpander): Stage2WriteToFs
    }

    interface Stage2WriteToFs {
        fun writeExpansionToFs(batch: MacroExpansionVfsBatch, stepNumber: Int): Stage3SaveToStorage
    }

    interface Stage3SaveToStorage {
        fun save(storage: ExpandedMacroStorage)
    }
}

object EmptyPipeline : Pipeline.Stage2WriteToFs, Pipeline.Stage3SaveToStorage {
    override fun writeExpansionToFs(batch: MacroExpansionVfsBatch, stepNumber: Int): Pipeline.Stage3SaveToStorage = this
    override fun save(storage: ExpandedMacroStorage) {}
}

object InvalidationPipeline {
    class Stage1(val info: ExpandedMacroInfo) : Pipeline.Stage1ResolveAndExpand {
        override fun expand(project: Project, expander: MacroExpander): Pipeline.Stage2WriteToFs = Stage2(info)
    }

    class Stage2(val info: ExpandedMacroInfo) : Pipeline.Stage2WriteToFs {
        override fun writeExpansionToFs(batch: MacroExpansionVfsBatch, stepNumber: Int): Pipeline.Stage3SaveToStorage {
            val expansionFile = info.expansionFile
            if (expansionFile != null && expansionFile.isValid) {
                batch.deleteFile(batch.resolve(expansionFile))
            }
            return Stage3(info)
        }
    }

    class Stage3(val info: ExpandedMacroInfo) : Pipeline.Stage3SaveToStorage {
        override fun save(storage: ExpandedMacroStorage) {
            checkWriteAccessAllowed()
            storage.removeInvalidInfo(info, true)
        }
    }
}

class RemoveSourceFileIfEmptyPipeline(private val sf: SourceFile) : Pipeline.Stage1ResolveAndExpand,
                                                                    Pipeline.Stage2WriteToFs,
                                                                    Pipeline.Stage3SaveToStorage {

    override fun expand(project: Project, expander: MacroExpander): Pipeline.Stage2WriteToFs = this
    override fun writeExpansionToFs(batch: MacroExpansionVfsBatch, stepNumber: Int): Pipeline.Stage3SaveToStorage = this
    override fun save(storage: ExpandedMacroStorage) {
        checkWriteAccessAllowed()
        storage.removeSourceFileIfEmpty(sf)
    }
}

object ExpansionPipeline {
    class Stage1(
        val call: RsMacroCall,
        val info: ExpandedMacroInfo
    ) : Pipeline.Stage1ResolveAndExpand {
        override fun expand(project: Project, expander: MacroExpander): Pipeline.Stage2WriteToFs {
            checkReadAccessAllowed() // Needed to access PSI (including resolve & expansion)
            checkIsSmartMode(project) // Needed to resolve macros
            if (!call.isValid) {
                return InvalidationPipeline.Stage2(info)
            }
            val callHash = call.bodyHash
            val oldExpansionFile = info.expansionFile
            val def = RsMacroPathReferenceImpl.resolveInBatchMode { call.resolveToMacro() }
                ?: return if (oldExpansionFile == null) EmptyPipeline else nextStageFail(callHash, null)

            val defHash = def.bodyHash

            if (info.isUpToDate(call, def)) {
                return EmptyPipeline // old expansion is up-to-date
            }

            val expansion = expander.expandMacroAsText(def, call)
            if (expansion == null) {
                MACRO_LOG.debug("Failed to expand macro: `${call.path.referenceName}!(${call.macroBody})`")
                return nextStageFail(callHash, defHash)
            }

            val expansionText = expansion.first.toString()
            val ranges = expansion.second

            if (oldExpansionFile != null && oldExpansionFile.isValid) {
                val oldExpansionText = VfsUtil.loadText(oldExpansionFile)
                if (expansionText == oldExpansionText) {
                    // Expansion text isn't changed, but [callHash] or [defHash] or [ranges]
                    // are changed and should be updated
                    return Stage2OkRangesOnly(info, callHash, defHash, oldExpansionFile, ranges)
                }
            }

            return nextStageOk(callHash, defHash, expansionText, ranges)
        }

        private fun nextStageFail(callHash: HashCode?, defHash: HashCode?): Pipeline.Stage2WriteToFs =
            Stage2Fail(info, callHash, defHash)

        private fun nextStageOk(
            callHash: HashCode?,
            defHash: HashCode?,
            expansionText: String,
            ranges: RangeMap
        ): Pipeline.Stage2WriteToFs =
            Stage2Ok(info, callHash, defHash, expansionText, ranges)

        override fun toString(): String =
            "ExpansionPipeline.Stage1(call=${call.path.referenceName}!(${call.macroBody}))"
    }

    class Stage2Ok(
        private val info: ExpandedMacroInfo,
        private val callHash: HashCode?,
        private val defHash: HashCode?,
        private val expansionText: String,
        private val ranges: RangeMap
    ) : Pipeline.Stage2WriteToFs {
        override fun writeExpansionToFs(batch: MacroExpansionVfsBatch, stepNumber: Int): Pipeline.Stage3SaveToStorage {
            val oldExpansionFile = info.expansionFile
            val file = if (oldExpansionFile != null) {
                check(oldExpansionFile.isValid) { "VirtualFile is not valid ${oldExpansionFile.url}" }
                val file = batch.resolve(oldExpansionFile)
                batch.writeFile(file, expansionText)
                file
            } else {
                batch.createFileWithContent(expansionText, stepNumber)
            }
            return Stage3(info, callHash, defHash, file, ranges)
        }
    }

    class Stage2OkRangesOnly(
        private val info: ExpandedMacroInfo,
        private val callHash: HashCode?,
        private val defHash: HashCode?,
        private val oldExpansionFile: VirtualFile,
        private val ranges: RangeMap
    ) : Pipeline.Stage2WriteToFs {
        override fun writeExpansionToFs(batch: MacroExpansionVfsBatch, stepNumber: Int): Pipeline.Stage3SaveToStorage {
            val file = batch.resolve(oldExpansionFile)
            return Stage3(info, callHash, defHash, file, ranges)
        }
    }

    class Stage2Fail(
        private val info: ExpandedMacroInfo,
        private val callHash: HashCode?,
        private val defHash: HashCode?
    ) : Pipeline.Stage2WriteToFs {
        override fun writeExpansionToFs(batch: MacroExpansionVfsBatch, stepNumber: Int): Pipeline.Stage3SaveToStorage {
            val oldExpansionFile = info.expansionFile
            if (oldExpansionFile != null && oldExpansionFile.isValid) {
                batch.deleteFile(batch.resolve(oldExpansionFile))
            }
            return Stage3(info, callHash, defHash, null, null)
        }
    }

    class Stage3(
        private val info: ExpandedMacroInfo,
        private val callHash: HashCode?,
        private val defHash: HashCode?,
        private val expansionFile: MacroExpansionVfsBatch.Path?,
        private val ranges: RangeMap?
    ) : Pipeline.Stage3SaveToStorage {
        override fun save(storage: ExpandedMacroStorage) {
            checkWriteAccessAllowed()
            val virtualFile = expansionFile?.toVirtualFile()
            storage.addExpandedMacro(info, callHash, defHash, virtualFile, ranges)
            // If a document exists for expansion file (e.g. when AST tree is loaded), the changes in
            // a virtual file will not be committed to the PSI immediately. We have to commit it manually
            // to see the changes (or somehow wait for DocumentCommitThread, but it isn't possible for now)
            if (virtualFile != null) {
                val doc = FileDocumentManager.getInstance().getCachedDocument(virtualFile)
                if (doc != null) {
                    PsiDocumentManager.getInstance(storage.project).commitDocument(doc)
                }
            }
        }
    }
}

val RsMacroCall.isTopLevelExpansion: Boolean
    get() = parent is RsMod || parent is RsMembers
