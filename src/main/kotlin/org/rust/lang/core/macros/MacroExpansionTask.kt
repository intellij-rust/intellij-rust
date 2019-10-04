/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.macros

import com.intellij.openapi.application.AccessToken
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDocumentManager
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
import java.nio.file.Path
import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.max
import kotlin.streams.toList
import kotlin.system.measureNanoTime

abstract class MacroExpansionTaskBase(
    project: Project,
    private val storage: ExpandedMacroStorage,
    private val pool: Executor,
    private val realFsExpansionContentRoot: Path
) : Task.Backgroundable(project, "Expanding Rust macros", /* canBeCancelled = */ false) {
    private val transactionExecutor = TransactionExecutor(project)
    private val expander = MacroExpander(project)
    private val sync = CountDownLatch(1)
    private val estimateStages = AtomicInteger()
    private val doneStages = AtomicInteger()
    private val currentStep = AtomicInteger()
    private val totalExpanded = AtomicInteger()
    private lateinit var realTaskIndicator: ProgressIndicator
    private val subTaskIndicator: ProgressIndicator = EmptyProgressIndicator()
    private lateinit var expansionSteps: Iterator<List<Extractable>>
    @Volatile
    private var heavyProcessRequested = false

    override fun run(indicator: ProgressIndicator) {
        checkReadAccessNotAllowed()
        indicator.checkCanceled()
        indicator.isIndeterminate = false
        realTaskIndicator = indicator

        expansionSteps = getMacrosToExpand().iterator()

        indicator.checkCanceled()
        var heavyProcessToken: AccessToken? = null
        try {
            submitExpansionTask()

            MACRO_LOG.trace("Awaiting")
            val millis = measureNanoTime {
                // 50ms - default progress bar update interval. See [ProgressDialog.UPDATE_INTERVAL]
                while (!sync.await(50, TimeUnit.MILLISECONDS)) {
                    indicator.fraction = calcProgress(
                        currentStep.get(),
                        doneStages.get().toDouble() / max(estimateStages.get(), 1)
                    )

                    // Type of [indicator] can be [BackgroundableProcessIndicator] which is thread sensitive
                    // and its `checkCanceled` method should be used only from a single thread.
                    // So we propagating cancellation. See [ProgressWindow.MyDelegate.checkCanceled].
                    if (indicator.isCanceled && !subTaskIndicator.isCanceled) subTaskIndicator.cancel()

                    // If project is disposed, then queue will be disposed too, so we shouldn't await sub task finish
                    // (and sub task may not call `sync.countDown()`, so without this `break` we will be blocked
                    // forever)
                    if (project.isDisposed) break

                    // Enter heavy process mode only if at least one macros is not up-to-date
                    if (heavyProcessToken == null && heavyProcessRequested) {
                        heavyProcessToken = HeavyProcessLatch.INSTANCE.processStarted("Expanding Rust macros")
                    }
                }
            }
            MACRO_LOG.trace("Task completed! ${totalExpanded.get()} total calls, millis: " + millis / 1_000_000)
        } finally {
            RsResolveCache.getInstance(project).endExpandingMacros()
            heavyProcessToken?.finish()
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
                    currentStep.incrementAndGet()
                } while (extractableList != null && extractableList.isEmpty())
                extractableList
            }
        }

        if (extractableList == null) {
            sync.countDown()
            return
        }

        realTaskIndicator.text = "Expanding Rust macros. Step " + currentStep.get() + "/$DEFAULT_RECURSION_LIMIT"
        estimateStages.set(0)
        doneStages.set(0)

        // All subsequent parallelStream tasks are executed on the same [pool]
        supplyAsync(pool) {
            realTaskIndicator.text2 = "Expanding macros"

            val stages1 = extractableList.parallelStream().unordered().flatMap { extractable ->
                executeUnderProgressWithWriteActionPriorityWithRetries(subTaskIndicator) {
                    // We need smart mode because rebind can be performed
                    runReadActionInSmartMode(project) {
                        val result = extractable.extract()
                        estimateStages.addAndGet(result.size * Pipeline.STAGES)
                        result.stream()
                    }
                }
            }.toList()

            val stages2 = stages1.parallelStream().unordered().map { stage1 ->
                val result = executeUnderProgressWithWriteActionPriorityWithRetries(subTaskIndicator) {
                    // We need smart mode to resolve macros
                    runReadActionInSmartMode(project) {
                        stage1.expand(project, expander)
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
                val stages3fs = stages2.chunked(VFS_BATCH_SIZE).map { stages2c ->
                    val fs = LocalFsMacroExpansionVfsBatch(realFsExpansionContentRoot)
                    val stages3 = stages2c.map { stage2 ->
                        if (project.isDisposed) throw ProcessCanceledException()
                        val result = stage2.writeExpansionToFs(fs)
                        doneStages.incrementAndGet()
                        result
                    }
                    stages3 to fs
                }

                // Note that if project is disposed, this task will not be executed or may be executed partially
                executeSequentially(transactionExecutor, stages3fs) { (stages3, fs) ->
                    runWriteAction {
                        fs.applyToVfs()
                        for (stage3 in stages3) {
                            stage3.save(storage)
                            doneStages.incrementAndGet()
                        }
                    }
                    totalExpanded.addAndGet(stages3.size)
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

    protected abstract fun getMacrosToExpand(): Sequence<List<Extractable>>

    open fun canEat(other: MacroExpansionTaskBase): Boolean = false

    open val isProgressBarDelayed: Boolean get() = true

    companion object {
        /**
         * Higher values leads to better throughput (overall expansion time), but worse latency (UI freezes)
         */
        private const val VFS_BATCH_SIZE = 50
    }
}

interface Extractable {
    fun extract(): List<Pipeline.Stage1ResolveAndExpand>
}

object Pipeline {
    const val STAGES: Int = 3

    interface Stage1ResolveAndExpand {
        /** must be a pure function */
        fun expand(project: Project, expander: MacroExpander): Stage2WriteToFs
    }

    interface Stage2WriteToFs {
        fun writeExpansionToFs(fs: MacroExpansionVfsBatch): Stage3SaveToStorage
    }

    interface Stage3SaveToStorage {
        fun save(storage: ExpandedMacroStorage)
    }
}

object EmptyPipeline : Pipeline.Stage2WriteToFs, Pipeline.Stage3SaveToStorage {
    override fun writeExpansionToFs(fs: MacroExpansionVfsBatch): Pipeline.Stage3SaveToStorage = this
    override fun save(storage: ExpandedMacroStorage) {}
}

object InvalidationPipeline {
    class Stage1(val info: ExpandedMacroInfo) : Pipeline.Stage1ResolveAndExpand {
        override fun expand(project: Project, expander: MacroExpander): Pipeline.Stage2WriteToFs = Stage2(info)
    }

    class Stage2(val info: ExpandedMacroInfo) : Pipeline.Stage2WriteToFs {
        override fun writeExpansionToFs(fs: MacroExpansionVfsBatch): Pipeline.Stage3SaveToStorage {
            val expansionFile = info.expansionFile
            if (expansionFile != null && expansionFile.isValid) {
                fs.deleteFile(fs.resolve(expansionFile))
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
                return if (oldExpansionFile == null) EmptyPipeline else nextStageFail(callHash, defHash)
            }

            val expansionText = expansion.first.toString()
            val ranges = expansion.second

            if (oldExpansionFile != null && oldExpansionFile.isValid) {
                val oldExpansionText = VfsUtil.loadText(oldExpansionFile)
                if (expansionText == oldExpansionText) {
                    val oldRanges = oldExpansionFile.loadRangeMap()
                    return if (ranges != oldRanges) {
                        Stage2OkRangesOnly(call, info, callHash, defHash, oldExpansionFile, ranges)
                    } else {
                        EmptyPipeline
                    }
                }
            }

            return nextStageOk(callHash, defHash, expansionText, ranges)
        }

        private fun nextStageFail(callHash: HashCode?, defHash: HashCode?): Pipeline.Stage2WriteToFs =
            Stage2Fail(call, info, callHash, defHash)

        private fun nextStageOk(
            callHash: HashCode?,
            defHash: HashCode?,
            expansionText: String,
            ranges: RangeMap
        ): Pipeline.Stage2WriteToFs =
            Stage2Ok(call, info, callHash, defHash, expansionText, ranges)

        override fun toString(): String =
            "ExpansionPipeline.Stage1(call=${call.path.referenceName}!(${call.macroBody}))"
    }

    class Stage2Ok(
        private val call: RsMacroCall,
        private val info: ExpandedMacroInfo,
        private val callHash: HashCode?,
        private val defHash: HashCode?,
        private val expansionText: String,
        private val ranges: RangeMap
    ) : Pipeline.Stage2WriteToFs {
        override fun writeExpansionToFs(fs: MacroExpansionVfsBatch): Pipeline.Stage3SaveToStorage {
            val oldExpansionFile = info.expansionFile
            val file = if (oldExpansionFile != null) {
                check(oldExpansionFile.isValid) { "VirtualFile is not valid ${oldExpansionFile.url}" }
                val file = fs.resolve(oldExpansionFile)
                fs.writeFile(file, expansionText)
                file
            } else {
                fs.createFileWithContent(expansionText)
            }
            return Stage3(call, info, callHash, defHash, file, ranges)
        }
    }

    class Stage2OkRangesOnly(
        private val call: RsMacroCall,
        private val info: ExpandedMacroInfo,
        private val callHash: HashCode?,
        private val defHash: HashCode?,
        private val oldExpansionFile: VirtualFile,
        private val ranges: RangeMap
    ) : Pipeline.Stage2WriteToFs {
        override fun writeExpansionToFs(fs: MacroExpansionVfsBatch): Pipeline.Stage3SaveToStorage {
            val file = fs.resolve(oldExpansionFile)
            return Stage3(call, info, callHash, defHash, file, ranges)
        }
    }

    class Stage2Fail(
        private val call: RsMacroCall,
        private val info: ExpandedMacroInfo,
        private val callHash: HashCode?,
        private val defHash: HashCode?
    ) : Pipeline.Stage2WriteToFs {
        override fun writeExpansionToFs(fs: MacroExpansionVfsBatch): Pipeline.Stage3SaveToStorage {
            val oldExpansionFile = info.expansionFile
            if (oldExpansionFile != null && oldExpansionFile.isValid) {
                fs.deleteFile(fs.resolve(oldExpansionFile))
            }
            return Stage3(call, info, callHash, defHash, null, null)
        }
    }

    class Stage3(
        private val call: RsMacroCall,
        private val info: ExpandedMacroInfo,
        private val callHash: HashCode?,
        private val defHash: HashCode?,
        private val expansionFile: MacroExpansionVfsBatch.Path?,
        private val ranges: RangeMap?
    ) : Pipeline.Stage3SaveToStorage {
        override fun save(storage: ExpandedMacroStorage) {
            checkWriteAccessAllowed()
            val virtualFile = expansionFile?.toVirtualFile()
            storage.addExpandedMacro(call, info, callHash, defHash, virtualFile, ranges)
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
