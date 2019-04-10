/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.macros

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.util.io.storage.HeavyProcessLatch
import org.rust.lang.core.psi.RsMacro
import org.rust.lang.core.psi.RsMacroCall
import org.rust.lang.core.psi.RsMembers
import org.rust.lang.core.psi.ext.RsMod
import org.rust.lang.core.psi.ext.macroBody
import org.rust.lang.core.psi.ext.resolveToMacro
import org.rust.lang.core.resolve.DEFAULT_RECURSION_LIMIT
import org.rust.openapiext.*
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

    override fun run(indicator: ProgressIndicator) {
        checkReadAccessNotAllowed()
        indicator.checkCanceled()
        indicator.isIndeterminate = false
        realTaskIndicator = indicator

        expansionSteps = getMacrosToExpand().iterator()

        indicator.checkCanceled()
        HeavyProcessLatch.INSTANCE.processStarted("Expanding Rust macros").use {
            submitExpansionTask()

            MACRO_LOG.trace("Awaiting")
            val millis = measureNanoTime {
                // 50ms - default progress bar update interval. See [ProgressDialog.UPDATE_INTERVAL]
                while (!sync.await(50, TimeUnit.MILLISECONDS)) {
                    indicator.fraction = (currentStep.get() - 1.0 + (doneStages.get().toDouble() / max(estimateStages.get(), 1))) / DEFAULT_RECURSION_LIMIT

                    // Type of [indicator] can be [BackgroundableProcessIndicator] which is thread sensitive
                    // and its `checkCanceled` method should be used only from a single thread.
                    // So we propagating cancellation. See [ProgressWindow.MyDelegate.checkCanceled].
                    if (indicator.isCanceled && !subTaskIndicator.isCanceled) subTaskIndicator.cancel()
                }
            }
            MACRO_LOG.trace("Task completed! ${totalExpanded.get()} total calls, millis: " + millis / 1_000_000)
        }
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
        estimateStages.set(extractableList.size)
        doneStages.set(0)

        val pool = ForkJoinPool.commonPool()
        supplyAsync(pool) {
            realTaskIndicator.text2 = "Expanding macros"

            val stages2 = extractableList.parallelStream().unordered().flatMap { extractable ->
                executeUnderProgress(subTaskIndicator) {
                    // We need smart mode because rebind can be performed
                    runReadActionInSmartMode(project) {
                        val result = extractable.extract()
                        doneStages.incrementAndGet()
                        estimateStages.addAndGet(result.size * Pipeline.STAGES)
                        result.stream()
                    }
                }
            }.map { stage1 ->
                val result = executeUnderProgressWithWriteActionPriorityWithRetries(subTaskIndicator) {
                    // We need smart mode to resolve macros
                    runReadActionInSmartMode(project) {
                        stage1.expand(project, expander)
                    }
                }
                doneStages.addAndGet(if (result is EmptyPipeline) Pipeline.STAGES else 1)
                result
            }.filter { it !is EmptyPipeline }.toList()

            realTaskIndicator.text2 = "Writing expansion results"

            if (stages2.isNotEmpty()) {
                // TODO support cancellation here
                val stages3fs = stages2.chunked(VFS_BATCH_SIZE).map { stages2c ->
                    val fs = LocalFsMacroExpansionVfsBatch(realFsExpansionContentRoot)
                    val stages3 = stages2c.map { stage2 ->
                        val result = stage2.writeExpansionToFs(fs)
                        doneStages.incrementAndGet()
                        result
                    }
                    stages3 to fs
                }

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
                val e = (t as? CompletionException)?.cause ?: t
                when (e) {
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
            info.expansionFile?.let { fs.deleteFile(fs.resolve(it)) }
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
            val oldExpansionFile = info.expansionFile
            val def = call.resolveToMacro()
                ?: return if (oldExpansionFile == null) EmptyPipeline else nextStage(null, null)

            if (info.isUpToDate(call, def)) {
                return EmptyPipeline // old expansion is up-to-date
            }
            val expansion = expander.expandMacroAsText(def, call)?.toString()
            if (expansion == null) {
                MACRO_LOG.debug("Failed to expand macro: `${call.path.referenceName}!(${call.macroBody})`")
            }

            if (oldExpansionFile != null && expansion != null && oldExpansionFile.isValid &&
                VfsUtil.loadText(oldExpansionFile) == expansion) {
                return EmptyPipeline
            }

            return nextStage(def, expansion)
        }

        private fun nextStage(def: RsMacro?, expansion: String?): Pipeline.Stage2WriteToFs =
            if (def != null && expansion != null) Stage2Ok(call, info, def, expansion) else Stage2Fail(call, info, def)

        override fun toString(): String =
            "ExpansionPipeline.Stage1(call=${call.path.referenceName}!(${call.macroBody}))"
    }

    class Stage2Ok(
        private val call: RsMacroCall,
        private val info: ExpandedMacroInfo,
        private val def: RsMacro,
        private val expansion: String
    ) : Pipeline.Stage2WriteToFs {
        override fun writeExpansionToFs(fs: MacroExpansionVfsBatch): Pipeline.Stage3SaveToStorage {
            val oldExpansionFile = info.expansionFile
            val file = if (oldExpansionFile != null) {
                check(oldExpansionFile.isValid) { "VirtualFile is not valid ${oldExpansionFile.url}" }
                val file = fs.resolve(oldExpansionFile)
                fs.writeFile(file, expansion)
                file
            } else {
                fs.createFileWithContent(expansion)
            }
            return Stage3(call, info, def, file)
        }
    }

    class Stage2Fail(
        private val call: RsMacroCall,
        private val info: ExpandedMacroInfo,
        private val def: RsMacro?
    ) : Pipeline.Stage2WriteToFs {
        override fun writeExpansionToFs(fs: MacroExpansionVfsBatch): Pipeline.Stage3SaveToStorage {
            val oldExpansionFile = info.expansionFile
            oldExpansionFile?.let { fs.deleteFile(fs.resolve(it)) }
            return Stage3(call, info, def, null)
        }
    }

    class Stage3(
        private val call: RsMacroCall,
        private val info: ExpandedMacroInfo,
        private val def: RsMacro?,
        private val expansionFile: MacroExpansionVfsBatch.Path?
    ) : Pipeline.Stage3SaveToStorage {
        override fun save(storage: ExpandedMacroStorage) {
            checkWriteAccessAllowed()
            val virtualFile = expansionFile?.toVirtualFile()
            storage.addExpandedMacro(call, info, def, virtualFile)
        }
    }
}

val RsMacroCall.isTopLevelExpansion: Boolean
    get() = parent is RsMod || parent is RsMembers
