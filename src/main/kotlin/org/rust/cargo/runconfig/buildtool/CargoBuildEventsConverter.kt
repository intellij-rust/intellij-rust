/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.runconfig.buildtool

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.JsonSyntaxException
import com.intellij.build.FilePosition
import com.intellij.build.events.BuildEvent
import com.intellij.build.events.MessageEvent
import com.intellij.build.events.StartEvent
import com.intellij.build.events.impl.*
import com.intellij.build.output.BuildOutputInstantReader
import com.intellij.build.output.BuildOutputParser
import com.intellij.openapi.progress.ProgressIndicator
import org.rust.cargo.toolchain.CargoTopMessage
import org.rust.cargo.toolchain.RustcMessage
import org.rust.cargo.toolchain.impl.CargoMetadata
import java.nio.file.Path
import java.nio.file.Paths
import java.util.function.Consumer

@Suppress("UnstableApiUsage")
class CargoBuildEventsConverter(private val context: CargoBuildContext) : BuildOutputParser {
    private val startEvents: MutableList<StartEvent> = mutableListOf()
    private val messageEvents: MutableSet<MessageEvent> = hashSetOf()

    private var jsonBuffer: String = ""

    private val rawBinaries: MutableSet<String> = hashSetOf()
    val binaries: List<Path> get() = rawBinaries.map { Paths.get(it) }

    override fun parse(
        line: String,
        reader: BuildOutputInstantReader,
        messageConsumer: Consumer<in BuildEvent>
    ): Boolean {
        val text = jsonBuffer + line
        if (text.startsWith("{")) {
            if (text.endsWith("}")) {
                jsonBuffer = ""
            } else {
                jsonBuffer = text + "\n"
                return true
            }
        }

        return try {
            val json = parseJsonObject(text)
            tryHandleRustcMessage(json, messageConsumer) || tryHandleRustcArtifact(json)
        } catch (e: JsonSyntaxException) {
            tryHandleCargoMessage(text, messageConsumer)
        }
    }

    fun parseOutput(
        line: String,
        stdOut: Boolean,
        messageConsumer: (BuildEvent) -> Unit
    ): Boolean {
        val message = try {
            val json = parseJsonObject(line)
            val rustcMessage = json?.let { CargoTopMessage.fromJson(it)?.message }
            rustcMessage?.rendered ?: return false
        } catch (e: JsonSyntaxException) {
            line
        }
        val formattedMessage = if (message.endsWith('\n')) message else message + '\n'
        val event = OutputBuildEventImpl(context.buildId, formattedMessage, stdOut)
        messageConsumer(event)
        return true
    }

    private fun tryHandleRustcMessage(json: JsonObject?, messageConsumer: Consumer<in BuildEvent>): Boolean {
        val topMessage = json?.let { CargoTopMessage.fromJson(it) } ?: return false
        val rustcMessage = topMessage.message

        val message = rustcMessage.message.trim().capitalize().trimEnd('.')
        if (message.startsWith("Aborting due")) return true
        val detailedMessage = rustcMessage.rendered

        val parentEventId = topMessage.package_id.substringBefore("(").trimEnd()

        val kind = getMessageKind(rustcMessage.level)
        if (kind == MessageEvent.Kind.SIMPLE) return true

        val filePosition = getFilePosition(rustcMessage)
        val messageEvent = createMessageEvent(parentEventId, kind, message, detailedMessage, filePosition)
        if (messageEvents.add(messageEvent)) {
            if (startEvents.none { it.id == parentEventId }) {
                handleCompilingMessage("Compiling $parentEventId", true, messageConsumer)
            }

            messageConsumer.accept(messageEvent)

            if (kind == MessageEvent.Kind.ERROR) {
                context.errors += 1
            } else {
                context.warnings += 1
            }
        }

        return true
    }

    private fun tryHandleRustcArtifact(json: JsonObject?): Boolean {
        val rustcArtifact = json?.let { CargoMetadata.Artifact.fromJson(it) } ?: return false

        val isSuitableTarget = when (rustcArtifact.target.cleanKind) {
            CargoMetadata.TargetKind.BIN -> true
            CargoMetadata.TargetKind.EXAMPLE -> {
                // TODO: support cases when crate types list contains not only binary
                rustcArtifact.target.cleanCrateTypes.singleOrNull() == CargoMetadata.CrateType.BIN
            }
            CargoMetadata.TargetKind.TEST -> true
            CargoMetadata.TargetKind.LIB -> rustcArtifact.profile.test
            else -> false
        }
        if (!isSuitableTarget || context.isTestBuild && !rustcArtifact.profile.test) return true

        val executable = rustcArtifact.executable
        if (executable != null) {
            rawBinaries.add(executable)
        } else {
            // BACKCOMPAT: Cargo 0.34.0
            rustcArtifact.filenames.filterNotTo(rawBinaries) { it.endsWith(".dSYM") }
        }

        return true
    }

    private fun tryHandleCargoMessage(line: String, messageConsumer: Consumer<in BuildEvent>): Boolean {
        val kind = getMessageKind(line.substringBefore(":"))
        val message = line
            .let { if (kind in ERROR_OR_WARNING) it.substringAfter(":") else it }
            .removePrefix(" internal compiler error:")
            .trim()
            .capitalize()
            .trimEnd('.')
        when {
            message.startsWith("Compiling") ->
                handleCompilingMessage(message, false, messageConsumer)
            message.startsWith("Fresh") ->
                handleCompilingMessage(message, true, messageConsumer)
            message.startsWith("Building") ->
                handleProgressMessage(line, messageConsumer)
            message.startsWith("Finished") ->
                handleFinishedMessage(null, messageConsumer)
            message.startsWith("Could not compile") -> {
                val taskName = message.substringAfter("`").substringBefore("`")
                handleFinishedMessage(taskName, messageConsumer)
            }
            kind in ERROR_OR_WARNING ->
                handleProblemMessage(kind, message, line, messageConsumer)
        }
        return true
    }

    private fun handleCompilingMessage(
        originalMessage: String,
        isUpToDate: Boolean,
        messageConsumer: Consumer<in BuildEvent>
    ) {
        val message = originalMessage.replace("Fresh", "Compiling").substringBefore("(").trimEnd()
        val eventId = message.substringAfter(" ").replace(" v", " ")
        val startEvent = StartEventImpl(eventId, context.buildId, System.currentTimeMillis(), message)
        messageConsumer.accept(startEvent)
        if (isUpToDate) {
            val finishEvent = FinishEventImpl(
                eventId,
                context.buildId,
                System.currentTimeMillis(),
                message,
                SuccessResultImpl(isUpToDate)
            )
            messageConsumer.accept(finishEvent)
        } else {
            startEvents.add(startEvent)
        }
    }

    private fun handleProgressMessage(message: String, messageConsumer: Consumer<in BuildEvent>) {

        fun finishNonActiveTasks(activeTaskNames: List<String>) {
            val startEventsIterator = startEvents.iterator()
            while (startEventsIterator.hasNext()) {
                val startEvent = startEventsIterator.next()
                val taskName = startEvent.taskName
                if (taskName !in activeTaskNames) {
                    startEventsIterator.remove()
                    val finishEvent = FinishEventImpl(
                        startEvent.id,
                        context.buildId,
                        System.currentTimeMillis(),
                        startEvent.message,
                        SuccessResultImpl()
                    )
                    messageConsumer.accept(finishEvent)
                }
            }
        }

        fun parseProgress(message: String): Progress {
            val result = PROGRESS_TOTAL_RE.find(message)?.destructured
            val current = result?.component1()?.toLongOrNull() ?: -1
            val total = result?.component2()?.toLongOrNull() ?: -1
            return Progress(current, total)
        }

        fun updateIndicator(indicator: ProgressIndicator, title: String, description: String, progress: Progress) {
            indicator.isIndeterminate = progress.total < 0
            indicator.text = title
            indicator.text2 = description
            indicator.fraction = progress.fraction
        }

        val activeTaskNames = message
            .substringAfter(":")
            .split(",")
            .map { it.substringBefore("(").trim() }
        finishNonActiveTasks(activeTaskNames)

        updateIndicator(
            context.indicator,
            context.progressTitle,
            message.substringAfter(":").trim(),
            parseProgress(message)
        )
    }

    private fun handleFinishedMessage(failedTaskName: String?, messageConsumer: Consumer<in BuildEvent>) {
        for (startEvent in startEvents) {
            val finishEvent = FinishEventImpl(
                startEvent.id,
                context.buildId,
                System.currentTimeMillis(),
                startEvent.message,
                when (failedTaskName) {
                    startEvent.taskName -> FailureResultImpl(null as Throwable?)
                    null -> SuccessResultImpl()
                    else -> SkippedResultImpl()
                }
            )
            messageConsumer.accept(finishEvent)
        }
    }

    private fun handleProblemMessage(
        kind: MessageEvent.Kind,
        message: String,
        detailedMessage: String?,
        messageConsumer: Consumer<in BuildEvent>
    ) {
        val messageEvent = createMessageEvent(context.buildId, kind, message, detailedMessage)
        if (messageEvents.add(messageEvent)) {
            messageConsumer.accept(messageEvent)
            if (kind == MessageEvent.Kind.ERROR) {
                context.errors += 1
            } else {
                context.warnings += 1
            }
        }
    }

    private fun getFilePosition(message: RustcMessage): FilePosition? {
        val span = message.mainSpan ?: return null
        val filePath = run {
            var filePath = Paths.get(span.file_name)
            if (!filePath.isAbsolute) {
                filePath = context.workingDirectory.resolve(filePath)
            }
            filePath
        }
        return FilePosition(
            filePath.toFile(),
            span.line_start - 1,
            span.column_start - 1,
            span.line_end - 1,
            span.column_end - 1
        )
    }

    companion object {
        const val RUSTC_MESSAGE_GROUP: String = "Rust compiler"

        private val PARSER: JsonParser = JsonParser()

        private val PROGRESS_TOTAL_RE: Regex = """(\d+)/(\d+)""".toRegex()

        private val ERROR_OR_WARNING: List<MessageEvent.Kind> =
            listOf(MessageEvent.Kind.ERROR, MessageEvent.Kind.WARNING)

        private fun parseJsonObject(line: String): JsonObject? =
            PARSER.parse(line).takeIf { it.isJsonObject }?.asJsonObject

        private fun getMessageKind(kind: String): MessageEvent.Kind =
            when (kind) {
                "error", "error: internal compiler error" -> MessageEvent.Kind.ERROR
                "warning" -> MessageEvent.Kind.WARNING
                else -> MessageEvent.Kind.SIMPLE
            }

        private fun createMessageEvent(
            parentEventId: Any,
            kind: MessageEvent.Kind,
            message: String,
            detailedMessage: String?,
            filePosition: FilePosition? = null
        ): MessageEvent = if (filePosition == null) {
            MessageEventImpl(parentEventId, kind, RUSTC_MESSAGE_GROUP, message, detailedMessage)
        } else {
            FileMessageEventImpl(parentEventId, kind, RUSTC_MESSAGE_GROUP, message, detailedMessage, filePosition)
        }

        private val StartEvent.taskName: String?
            get() = (id as? String)?.substringBefore(" ")?.substringBefore("(")?.trimEnd()

        private data class Progress(val current: Long, val total: Long) {
            val fraction: Double = current.toDouble() / total
        }
    }
}
