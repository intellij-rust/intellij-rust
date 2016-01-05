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
import java.util.regex.Pattern


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
        val processOut = collectOutput(process)

        for (line in processOut) {
            val match = """(.*):(\d+):(\d+): (\d+):(\d+) error:(.*)""".toRegex().matchEntire(line)
            if (match != null) {
                val groups = match.groups
                val file = groups[1]!!.value

                val sourcePath = getContentRootPath(module) + "/" + file.replace('\\', '/')

                val startLineNum = groups[2]!!.value.toLong()
                val startColNum = groups[3]!!.value.toLong()
                val msg = groups[6]!!.value

                context.processMessage(CompilerMessage(
                    "cargo",
                    BuildMessage.Kind.ERROR,
                    msg,
                    sourcePath,
                    -1L, -1L, -1L,
                    startLineNum, startColNum))
            }
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
