package org.rust.jps


import org.jetbrains.jps.ModuleChunk
import org.jetbrains.jps.builders.DirtyFilesHolder
import org.jetbrains.jps.builders.java.JavaSourceRootDescriptor
import org.jetbrains.jps.incremental.*
import org.jetbrains.jps.incremental.messages.BuildMessage
import org.jetbrains.jps.incremental.messages.CompilerMessage
import org.jetbrains.jps.model.module.JpsModule
import java.io.BufferedReader
import java.io.File
import java.io.IOException
import java.io.InputStreamReader


class RustBuilder : ModuleLevelBuilder(BuilderCategory.TRANSLATOR) {


    @Throws(ProjectBuildException::class, IOException::class)
    override fun build(context: CompileContext,
                       chunk: ModuleChunk,
                       dirtyFilesHolder: DirtyFilesHolder<JavaSourceRootDescriptor, ModuleBuildTarget>,
                       outputConsumer: ModuleLevelBuilder.OutputConsumer): ModuleLevelBuilder.ExitCode {

        for (module in chunk.modules) {
            if (module.moduleType !is RustJpsModuleType) {
                continue
            }

            context.processMessage(CompilerMessage("cargo", BuildMessage.Kind.INFO, "cargo build"))

            val path = getContentRootPath(module)

            val processBuilder = ProcessBuilder("cargo", "build")
            processBuilder.redirectErrorStream(true)
            processBuilder.directory(File(path))
            val process = processBuilder.start()
            processOut(module, context, process)
            try {
                process.waitFor()
            } catch (e: InterruptedException) {
                e.printStackTrace()
            }

            if (process.exitValue() != 0) {
                return ModuleLevelBuilder.ExitCode.ABORT
            }
        }

        return ModuleLevelBuilder.ExitCode.OK
    }

    @Throws(IOException::class)
    private fun processOut(module: JpsModule, context: CompileContext, process: Process) {
        // We want to process errors and warnings asynchronously, without waiting
        // for cargo to finish the build, hence the need in a complex state machine for matching.
        // Hopefully this machinery will become unnecessary once we start using JSON-formatted errors.
        val processOut = collectOutput(process)
        for (error in extractErrors(processOut)) {
            val sourcePath = getContentRootPath(module) + "/" + error.path.replace('\\', '/')
            context.processMessage(CompilerMessage(
                "cargo",
                if (error.kind == "warning") BuildMessage.Kind.WARNING else BuildMessage.Kind.ERROR,
                error.text,
                sourcePath,
                -1L, -1L, -1L,
                error.line, error.column))
        }
    }

    @Throws(IOException::class)
    private fun collectOutput(process: Process): Sequence<String> {
        val reader = BufferedReader(InputStreamReader(process.inputStream))
        return sequence() {
            reader.readLine();
        }
    }

    private fun getContentRootPath(module: JpsModule): String {
        val urls = module.contentRootsList.urls
        if (urls.size == 0) {
            throw RuntimeException("Can't find content root in module")
        }
        val url = urls[0]
        return url.substring("file://".length)
    }

    override fun getCompilableFileExtensions(): List<String> = listOf("rs")

    override fun toString() = presentableName

    override fun getPresentableName() = "Cargo builder"

}


data class RawErrorInfo(
    val path: String,
    val line: Long,
    val column: Long,
    val kind: String,
    val text: String
)


fun extractErrors(lines: Sequence<String>): Sequence<RawErrorInfo> {
    val matcher = ErrorMatchingDFA()
    val iterator = lines.iterator()
    return sequence {
        for (line in iterator) {
            matcher.feed(line)?.let { return@sequence it }
        }
        return@sequence matcher.feed(null)
    }
}


// No named groups in Kotlin yet :(
private val errorHeader = """(.*):(\d+):(\d+): (\d+):(\d+) (error|warning):(.*)""".toRegex()


private class ErrorMatchingDFA {
    fun feed(line: String?): RawErrorInfo? {
        val (newState, output) = state.feed(line)
        state = newState
        return output
    }

    private sealed class State {
        abstract fun feed(line: String?): Pair<State, RawErrorInfo?>

        class Initial: State() {
            override fun feed(line: String?): Pair<State, RawErrorInfo?> {
                line ?: return Pair(Done(), null)
                val match = errorHeader.matchEntire(line)
                match ?: return Pair(this, null)
                return Pair(MatchingError(match), null)
            }
        }

        class MatchingError(match: MatchResult): State() {
            private val path: String
            private val errorLine: Long
            private val errorColumn: Long
            private val kind: String
            private val buffer: StringBuffer = StringBuffer()

            init {
                appendErrorLine(match.value)
                val groups = match.groups
                path = groups[1]!!.value
                errorLine = groups[2]!!.value.toLong()
                errorColumn = groups[3]!!.value.toLong()
                kind = groups[6]!!.value
            }

            override fun feed(line: String?): Pair<State, RawErrorInfo?> {
                line ?: return Pair(Done(), errorInfo())
                val match = errorHeader.matchEntire(line)
                if (match == null) {
                    appendErrorLine(line)
                    return Pair(this, null)
                }
                return Pair(MatchingError(match), errorInfo())
            }

            private fun appendErrorLine(line: String) = with(buffer) {
                append(line)
                append('\n')
            }

            private fun errorInfo(): RawErrorInfo = RawErrorInfo(
                path,
                errorLine,
                errorColumn,
                kind,
                buffer.toString().trim()
            )
        }

        class Done: State() {
            override fun feed(line: String?): Pair<State, RawErrorInfo?> = Pair(this, null)
        }

    }

    private var state: State = State.Initial()
}
