/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.macros.proc

import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.core.io.JsonEOFException
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.module.SimpleModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.google.common.annotations.VisibleForTesting
import com.intellij.execution.configuration.EnvironmentVariablesData
import com.intellij.execution.process.ProcessIOExecutorService
import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.util.Disposer
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.io.createDirectories
import org.rust.cargo.toolchain.BacktraceMode
import org.rust.cargo.toolchain.RsToolchainBase
import org.rust.cargo.toolchain.wsl.RsWslToolchain
import org.rust.lang.core.macros.MACRO_LOG
import org.rust.lang.core.macros.tt.FlatTree
import org.rust.lang.core.macros.tt.FlatTreeJsonDeserializer
import org.rust.lang.core.macros.tt.FlatTreeJsonSerializer
import org.rust.openapiext.RsPathManager
import org.rust.openapiext.isUnitTestMode
import org.rust.stdext.*
import org.rust.stdext.RsResult.Err
import org.rust.stdext.RsResult.Ok
import java.io.*
import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.*
import java.util.concurrent.locks.Condition
import java.util.concurrent.locks.Lock
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

class ProcMacroServerPool private constructor(
    toolchain: RsToolchainBase,
    private val needsVersionCheck: Boolean,
    expanderExecutable: Path
) : Disposable {
    private val pool = Pool(4) {
        ProcMacroServerProcess.createAndRun(toolchain, expanderExecutable) // Throws ProcessCreationException
    }

    private val expanderVersion: RsResult<ProMacroExpanderVersion, RequestSendError> by cancelableLazy {
        sendInner(Request.ApiVersionCheck, 10_000).andThen {
            val i = (it as Response.ApiVersionCheck).version.toInt()
            when (val version = ProMacroExpanderVersion.from(i)) {
                null -> Err(RequestSendError.UnknownVersion(i))
                else -> Ok(version)
            }
        }
    }

    fun requestExpanderVersion(): RsResult<ProMacroExpanderVersion, RequestSendError> {
        if (!needsVersionCheck) {
            return Ok(ProMacroExpanderVersion.NO_VERSION_CHECK_VERSION)
        }
        return expanderVersion
    }

    init {
        Disposer.register(this, pool)
    }

    fun send(request: Request, timeout: Long): RsResult<Response, RequestSendError> {
        requestExpanderVersion().unwrapOrElse { return Err(it) }
        return sendInner(request, timeout)
    }

    private fun sendInner(request: Request, timeout: Long): RsResult<Response, RequestSendError> {
        val io = try {
            pool.alloc() // Throws ProcessCreationException
        } catch (e: ProcessCreationException) {
            return Err(RequestSendError.ProcessCreation(e))
        }
        return try {
            Ok(io.send(request, timeout)) // Throws IOException, TimeoutException
        } catch(e: IOException) {
            Err(RequestSendError.IO(e))
        } catch(e: TimeoutException) {
            Err(RequestSendError.Timeout(e))
        } finally {
            pool.free(io)
        }
    }

    override fun dispose() {}

    companion object {
        fun new(
            toolchain: RsToolchainBase,
            needsVersionCheck: Boolean,
            expanderExecutable: Path,
            parentDisposable: Disposable
        ): ProcMacroServerPool {
            return ProcMacroServerPool(toolchain, needsVersionCheck, expanderExecutable)
                .also { Disposer.register(parentDisposable, it) }
        }

        fun findExpanderExecutablePath(toolchain: RsToolchainBase, sysroot: String): Path? {
            return findExpanderFromToolchain(toolchain, sysroot) ?: findEmbeddedExpander(toolchain)
        }

        private fun findExpanderFromToolchain(toolchain: RsToolchainBase, sysroot: String): Path? {
            val binaryName = toolchain.getExecutableName("rust-analyzer-proc-macro-srv")
            val expanderPath = Path.of(sysroot, "libexec", binaryName)

            if (toolchain is RsWslToolchain) {
                // Path.isExecutable() doesn't work for WSL files
                if (!expanderPath.toFile().isFile) return null
            } else {
                if (!expanderPath.isExecutable()) return null
            }
            return expanderPath
        }

        private fun findEmbeddedExpander(toolchain: RsToolchainBase): Path? {
            return RsPathManager.nativeHelper(toolchain is RsWslToolchain)
        }
    }
}

sealed interface RequestSendError {
    class ProcessCreation(val e: ProcessCreationException): RequestSendError
    class IO(val e: IOException): RequestSendError
    class Timeout(val e: TimeoutException): RequestSendError
    class UnknownVersion(val version: Int): RequestSendError
}

private class Pool(
    private val limit: Int,
    private val supplier: () -> ProcMacroServerProcess
) : Disposable {
    private val stack: MutableList<ProcMacroServerProcess?> = mutableListOf()
    private val stackLock: Lock = ReentrantLock()
    private val stackIsNotEmpty: Condition = stackLock.newCondition()

    private val idleProcessCleaner: ScheduledFuture<*>

    @Volatile
    private var isDisposed: Boolean = false

    init {
        repeat(limit) {
            stack.add(null)
        }

        idleProcessCleaner = AppExecutorUtil.getAppScheduledExecutorService()
            .scheduleWithFixedDelay(::killIdleExpanders, MAX_IDLE_MILLIS, MAX_IDLE_MILLIS, TimeUnit.MILLISECONDS)
    }

    fun alloc(): ProcMacroServerProcess {
        check(!isDisposed)
        val value = stackLock.withLockAndCheckingCancelled {
            while (stack.isEmpty()) {
                stackIsNotEmpty.awaitWithCheckCancelled()
            }
            stack.removeLast()
        }
        return when {
            value == null -> supply()
            value.isValid -> value
            else -> {
                Disposer.dispose(value)
                supply()
            }
        }
    }

    private fun supply(): ProcMacroServerProcess {
        val newValue = try {
            supplier()
        } catch (t: Throwable) {
            free(null)
            throw t
        }
        Disposer.register(this, newValue)
        return newValue
    }

    fun free(t: ProcMacroServerProcess?) {
        stackLock.withLock {
            stack.add(t)
            stackIsNotEmpty.signal()
        }
    }

    private fun killIdleExpanders() {
        val processesToDispose = stackLock.withLock {
            stack.withIndex().mapNotNull { (i, process) ->
                if (process != null && process.idleTime > MAX_IDLE_MILLIS) {
                    stack[i] = null
                }
                process
            }
        }
        // Dispose without the lock
        for (process in processesToDispose) {
            Disposer.dispose(process)
        }
    }

    override fun dispose() {
        isDisposed = true
        idleProcessCleaner.cancel(false)
        if (stack.size != limit) {
            MACRO_LOG.error("Some processes were not freed! ${stack.size} != $limit")
        }
    }

    companion object {
        private const val MAX_IDLE_MILLIS: Long = 60_000
    }
}

/**
 * [ProcMacroServerProcess] is responsible for communicating with proc macro expander [process] and
 * manages its lifecycle.
 */
private class ProcMacroServerProcess private constructor(
    private val process: Process,
    private val isWsl: Boolean, // true if the process is running under Windows WSL
) : Runnable, Disposable {
    private val stdout: BufferedReader = BufferedReader(InputStreamReader(process.inputStream, Charsets.UTF_8))
    private val stdin: Writer = OutputStreamWriter(process.outputStream, Charsets.UTF_8)

    private val lock = ReentrantLock()
    private val requestQueue = SynchronousQueue<Pair<Request, CompletableFuture<Response>>>()
    private val task: Future<*> = ProcessIOExecutorService.INSTANCE.submit(this)

    @Volatile
    private var lastUsed: Long = System.currentTimeMillis()

    @Volatile
    private var isDisposed: Boolean = false

    private var isFirstRequest: Boolean = true

    @Throws(IOException::class, TimeoutException::class)
    fun send(request: Request, timeout: Long): Response {
        if (!lock.tryLock()) error("`send` must not be called from multiple threads simultaneously")
        return try {
            if (!process.isAlive) throw IOException("The process has been killed")
            val responseFuture = CompletableFuture<Response>()
            if (!requestQueue.offer(request to responseFuture, timeout, TimeUnit.MILLISECONDS)) {
                throw TimeoutException()
            }

            try {
                // throws TimeoutException, ExecutionException(cause = IOException), InterruptedException
                responseFuture.getWithCheckCanceled(timeout)
            } catch (e: ExecutionException) {
                // Unwrap exceptions from `writeAndRead` method
                throw e.cause ?: IllegalStateException("Unexpected ExecutionException without a cause", e)
            } catch (e: InterruptedException) {
                // Should not really happens
                throw ProcessCanceledException(e)
            }
        } catch (t: Throwable) {
            Disposer.dispose(this) // Kill the process (if not yet)
            throw t
        } finally {
            lastUsed = System.currentTimeMillis()
            lock.unlock()
        }
    }

    val isValid: Boolean
        get() = !isDisposed && process.isAlive

    val idleTime: Long
        get() = System.currentTimeMillis() - lastUsed

    override fun run() {
        try {
            while (!isDisposed) {
                val (request, responder) = try {
                    requestQueue.take() // Blocks until request is available or the `task` is cancelled
                } catch (ignored: InterruptedException) {
                    return // normal shutdown
                }
                val response = try {
                    writeAndRead(request)
                } catch (e: Throwable) {
                    responder.completeExceptionally(tryRefineException(e) ?: e)
                    return
                }
                isFirstRequest = false
                responder.complete(response)
            }
        } finally {
            if (!isDisposed) { // A very exceptional path. Normally `isDisposed` should be `true` at this point
                killProcess()
            }
        }
    }

    private fun tryRefineException(e: Throwable): Throwable? {
        // On WSL, a process usually starts without errors (because the root process is `bash`, I guess),
        // but then fails when trying to communicate with it.
        if (isWsl && isFirstRequest && e is IOException && e.message == "The pipe is being closed") {
            return ProcessCreationException(e)
        }

        // `EOFException` is likely means that the process has been exited; let's try to wait for
        // an exit code. Using a longer timeout for tests in order to avoid flaky tests
        val isEOF = e is EOFException || e is JsonEOFException // Reading exceptions
            || e is IOException && e.message == "Stream Closed" // Writing exceptions
        if (isEOF && process.waitFor(if (isUnitTestMode) 2000L else 100L, TimeUnit.MILLISECONDS)) {
            return ProcessAbortedException(e, process.exitValue())
        }

        return null
    }

    @Throws(IOException::class)
    private fun writeAndRead(request: Request): Response {
        ProcMacroJsonParser.JSON_MAPPER.writeValue(stdin, request)
        stdin.write("\n")
        stdin.flush()

        stdout.skipUntilJsonObject()

        return ProcMacroJsonParser.JSON_MAPPER.readValue(stdout, Response::class.java)
    }

    /**
     * A procedural macro can emit output using `println!()`.
     * Trying to skip it util `{` found
     */
    @Throws(IOException::class)
    private fun BufferedReader.skipUntilJsonObject() {
        while (true) {
            mark(1)
            val char = read()
            if (char == -1) throw EOFException()
            if (char == '{'.code) {
                reset()
                break
            }
        }
    }

    override fun dispose() {
        isDisposed = true
        task.cancel(true) // The thread will catch `InterruptedException` on `requestQueue.take()`
        killProcess()
    }

    private fun killProcess() {
        MACRO_LOG.debug { "Killing proc macro expander process (pid: ${process.pid()})" }
        process.destroyForcibly() // SIGKILL
    }

    companion object {
        private val workingDir: Path by lazy {
            try {
                RsPathManager.tempPluginDirInSystem().resolve("proc-macro-expander-pwd").apply {
                    createDirectories()
                    cleanDirectory()
                }
            } catch (e: IOException) {
                MACRO_LOG.error(e)
                Paths.get(".")
            }
        }

        @Throws(ProcessCreationException::class)
        fun createAndRun(toolchain: RsToolchainBase, expanderExecutable: Path): ProcMacroServerProcess {
            MACRO_LOG.debug { "Starting proc macro expander process $expanderExecutable" }

            val env = mapOf(
                "INTELLIJ_RUST" to "1", // Let a proc macro know that it is run from intellij-rust
                "RA_DONT_COPY_PROC_MACRO_DLL" to "1",
                "RUST_ANALYZER_INTERNALS_DO_NOT_USE" to "this is unstable",
            )
            val commandLine = toolchain.createGeneralCommandLine(
                expanderExecutable,
                workingDir,
                null,
                BacktraceMode.NO,
                EnvironmentVariablesData.create(env, true),
                emptyList(),
                emulateTerminal = false,
                withSudo = false,
                patchToRemote = true
            ).withRedirectErrorStream(false)

            val process: Process = try {
                commandLine.toProcessBuilder()
                    .redirectError(ProcessBuilder.Redirect.DISCARD)
                    .start()
            } catch (e: IOException) {
                throw ProcessCreationException(e)
            }

            MACRO_LOG.debug { "Started proc macro expander process (pid: ${process.pid()})" }

            return ProcMacroServerProcess(process, isWsl = toolchain is RsWslToolchain)
        }
    }
}

@VisibleForTesting
object ProcMacroJsonParser {
    val JSON_MAPPER: ObjectMapper = ObjectMapper()
        .configure(JsonGenerator.Feature.AUTO_CLOSE_TARGET, false)
        .configure(JsonGenerator.Feature.FLUSH_PASSED_TO_STREAM, false)
        .configure(JsonGenerator.Feature.AUTO_CLOSE_JSON_CONTENT, false)
        .configure(JsonParser.Feature.AUTO_CLOSE_SOURCE, false)
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
        .registerKotlinModule()
        .registerModule(
            SimpleModule()
                .addSerializer(Request::class.java, RequestJsonSerializer())
                .addDeserializer(Response::class.java, ResponseJsonDeserializer())
                .addDeserializer(FlatTree::class.java, FlatTreeJsonDeserializer)
                .addSerializer(FlatTree::class.java, FlatTreeJsonSerializer)
        )
}
