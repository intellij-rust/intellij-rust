package org.rust.ide.annotator

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.io.InputStream
import java.lang.reflect.Type

import kotlin.collections.*

import com.intellij.lang.annotation.*
import com.intellij.openapi.editor.*
import com.intellij.psi.PsiFile
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.openapi.diagnostic.Logger as IJLogger

import com.google.gson.*

import org.apache.commons.lang.StringEscapeUtils.escapeHtml

object IJDebug {
    val defaultTitle = "CARGO-CHECK"

    fun log(msg : String, title : String = defaultTitle) {
        IJLogger.getInstance(title).debug(msg)
    }

}

object CargoJson {

    private fun<T> checkExp(name : String, actual : T, expected : T, forContext : String = "") {
        if (actual == expected)
            return

        val ctxStr = if (forContext != "") "for $forContext " else ""
        val err = "Unexpected $name `$actual` $ctxStr(expected `$expected`)"

        throw RuntimeException(err)
    }

    data class Target(
        val kind: List<String>,
        val name : String,
        val src_path : String)

    @Suppress("UNUSED")
    //These are special compiler messages that we're going to ignore.
    data class CompilerMessage(
        val features : JsonObject,
        val filenames : List<String>,
        val package_id: String,
        val profile : Profile,
        val reason : String,
        val target : Target
        ) {
        data class Profile(
            val debug_assertions : Boolean,
            val debuginfo : Int,
            val opt_level : String,
            val test : Boolean
            )
    }

    //A top level compiler message
    data class TopMessage(
        val message: Message,
        @Suppress("UNUSED")
        val package_id : String,
        val reason : String,
        val target : Target
    ) {
        init {
            checkExp("reason", reason, "compiler-message")
            checkExp("target.kind", target.kind, listOf("bin"))
        }

        abstract class InfoMessageBase(override val message: String, override val spans : List<Span>) : Message() {

            override fun doAnnotate(holder: AnnotationHolder, target: Target) {
                //We don't expect these to be called directly.
                //We only expect help messages as children of error messages

            }
        }

        class HelpMessage(message: String, spans: List<Span>) : InfoMessageBase(message, spans)
        class NoteMessage(message: String, spans: List<Span>) : InfoMessageBase(message, spans)

        //Used for both error and warning
        abstract class ErrorBaseMessage(
            val children: List<Message>,
            val code: Code?,
            override val message: String,
            override val spans: List<Span>,
            val level : String //Only "warning" and "error" allowed
        ) : Message() {
            init {
                children.forEach { assert(it is NoteMessage || it is HelpMessage) }
            }

            //Check if this we don't need to do annotation for this error
            //and can skip it.
            private fun canSkipAnnotation() : Boolean {
                if (code != null || children.isNotEmpty() || spans.isNotEmpty())
                    return false

                //Check the message itself - some messages don't need to be displayed as errors
                val regexes = arrayOf(
                    Regex("aborting due to \\d+ previous errors"),
                    Regex("aborting due to previous error")
                )
                val sanitizedMessage = message.trim()

                return regexes.any { sanitizedMessage.matches(it) }
            }

            override fun doAnnotate(holder: AnnotationHolder, target: Target) {
                if (canSkipAnnotation()) {
                    //IJDebug.log("skipping annotation for error $message")
                    return
                }

                //Next we do the span annotations
                val annotFile = holder.currentAnnotationSession.file.virtualFile.name
                val annotFilePath = holder.currentAnnotationSession.file.virtualFile.path
                val severity = when (level) {
                    "error" -> HighlightSeverity.ERROR
                    "warning" -> HighlightSeverity.WARNING
                    else -> { throw AssertionError() }
                }
                //Can't have hyperlinks in the info. We'll include it in the tooltip instead.
                val codeStr = if (code != null) "Code: ${code.formatAsLink()}" else ""
                val shortErrorStr = message

                //If spans are empty we add a "global" error
                if (spans.isEmpty()) {
                    if (target.src_path != annotFilePath) {
                        //not main file, skip global annotation
                    } else {
                        //add a global annotation
                        val annot = holder.createAnnotation(severity, TextRange.EMPTY_RANGE, shortErrorStr)

                        annot.isFileLevelAnnotation = true
                        annot.setNeedsUpdateOnTyping(true)
                        annot.tooltip = escapeHtml(message) + if(codeStr != "") "<hr/>$codeStr" else ""

                    }
                    return
                }

                //Each span has a label and a text range
                //The error itself is relevant for all of them.
                //So first we create the "extra error info" that will be displayed below the span label
                //and then we use it for all spans.

                //Get all children (help/note) messages - including those with spans. We include the notes with
                //spans twice - once in this message and once in their own annotation relevant to their span.
                //This is because the main error lacks context when the notes are missing.
                //FIXME: Add a hyperlink to the info annotations here and connect it to the annotation listener.
                val childrenMsg = children.map { (if (it is NoteMessage) "Note: " else "Help: ") + escapeHtml(it.message) }.joinToString("<br/>")

                //Create a problem group to group all annotations
                val problemGroup = ProblemGroup { shortErrorStr }

                //Generate annotations for all spans of this error message plus of the children messages with spans
                val allSpans = (listOf(this) + children).map {it.getAllSpans()}.flatten()

                IJDebug.log("doing annotations for file $annotFile (${allSpans.size} spans)")
                allSpans.filter {
                    //First filter all spans not relevant for this file
                    annotFilePath == it.file_name
                }.forEach {
                    //Next create the annotations for the spans of this file
                    //The byte_start/byte_end numbers seem to be completely inaccurate for
                    //files other than the main one.
                    //So we use the line and column number instead.
                    val doc = holder.currentAnnotationSession.file.viewProvider.document ?: throw AssertionError()
                    fun toOfs(line:Int, col: Int) : Int{
                        val lineStart = doc.getLineStartOffset(line)
                        val lineEnd = doc.getLineEndOffset(line)
                        @Suppress("UNUSED_VARIABLE")
                        val colsInLine = lineEnd - lineStart

                        //col == colsInLine means end of line?
                        //It is possible to get it in the compiler message

                        //We pass on this check since the compiler seems to violate it liberally
                        //if (col > colsInLine)
                            //throw AssertionError()

                        return lineStart + col
                    }

                    //FIXME: Sometimes rustc outputs an end line smaller than the start line.
                    //       Assuming this is a bug in rustc and not a feature, this condition should be
                    //       reverted to an assert in the future.
                    if (it.line_end < it.line_start || it.line_end == it.line_start && it.column_end < it.column_start)
                        return@forEach

                    //The compiler message lines and columns are 1 based while intellij idea are 0 based
                    val textRange = TextRange(toOfs(it.line_start - 1, it.column_start - 1), toOfs(it.line_end - 1, it.column_end - 1))

                    val short = if (it.is_primary) {
                        //Special case - if this is a primary trait then we have to add
                        //the message text (since it may have a label but the label is only extra info).
                        message
                    } else {
                        //Short message is the description. If there's a label we use it,
                        //and if not we use the original error message.
                        //In any case we attach the error code if there.
                        it.label ?: message
                    }

                    //In the tooltip we give additional info - the children messages
                    val extra = (if(codeStr !="") "$codeStr<br/>" else "") +
                        (if (it.is_primary && it.label != null) "${escapeHtml(it.label)}<br/>" else "") +
                        childrenMsg
                    val tooltip = short + if (extra.isBlank()) "" else "<hr/>" + extra

                    //IJDebug.log("adding annotation (short=$short, ${EI("tooltip", tooltip)})")
                    val spanSeverity = if (it.is_primary) severity else HighlightSeverity.INFORMATION // HighlightSeverity.WEAK_WARNING

                    val annot = holder.createAnnotation(spanSeverity, textRange, short)

                    //See @holder.createAnnotation with tooltip for why we wrap the message like this
                    annot!!.tooltip = "<html>${escapeHtml(tooltip)}</html>"
                    annot.problemGroup = problemGroup
                    annot.setNeedsUpdateOnTyping(true)

                }
            }
        }

        class ErrorMessage(children: List<Message>, code: Code?, message: String, spans: List<Span>) :
            ErrorBaseMessage(children, code, message, spans, "error")
        class WarningMessage(children: List<Message>, code: Code?, message: String, spans: List<Span>) :
            ErrorBaseMessage(children, code, message, spans, "warning")

        abstract class Message  {

            abstract val message : String
            abstract val spans : List<Span>

            //Return all the spans of this message including their child spans
            fun getAllSpans() : List<Span> {
                return spans.map{it.getAllSpans()}.flatten()
            }

            abstract fun doAnnotate(holder: AnnotationHolder, target: Target)

            companion object {

                class MessageFactory : JsonDeserializer<Message> {
                    override fun deserialize(json: JsonElement?, typeOfT: Type?, context: JsonDeserializationContext?): Message {

                        val flat : FlatMessage = context!!.deserialize(json, FlatMessage::class.java)

                        return flat.toMessage()
                    }
                }

                private data class FlatMessage(
                    val children: List<Message>,
                    val code: Code?,
                    val level: String,
                    val message: String,
                    //It is unclear what this is but it may be a string.
                    val rendered: String?,
                    val spans: List<Message.Span>) {

                    fun toMessage() : Message {
                        val ctx = "a message of type `$level`"

                        //assert(rendered == null)
                        /*
                        if (rendered != null)
                            throw RuntimeException("Unrecognized value for \"rendered\" field (`${rendered}`) in rust compiler message ")
                            */
                        if (level != "error" && level != "warning") {
                            //These are checks for degenerate messages - i.e. messages with only a message string.
                            //The types are: note, help, (error && code==null)

                            checkExp("code", code, null, ctx)
                            checkExp("children size", children.size, 0, ctx)

                        }

                        when (level) {
                            "error", "warning" -> {
                                //Children may be any number (0 or bigger)
                                //code may be null
                                //spans may be any number

                                when (level) {
                                    "error" -> return ErrorMessage(children, code, message, spans)
                                    "warning" -> return WarningMessage(children, code, message, spans)
                                    else -> throw AssertionError()
                                }

                            }
                            "help" -> return HelpMessage(message, spans)
                            "note" -> return NoteMessage(message, spans)

                            else -> throw RuntimeException("Unrecognized level `$level` in rust compiler message")
                        }
                    }
                }

            }

            //An error code (?)
            data class Code(val code : String, val explanation: String?) {

                fun formatAsLink() : String {
                    val rustErrorIndexPrefix = "https://doc.rust-lang.org/error-index.html"

                    return "<a href=\"$rustErrorIndexPrefix#$code\">$code</a>"
                }
            }

            //Code spans (?)
            data class Span(
                val byte_end : Int,
                val byte_start : Int,
                val column_end : Int,
                val column_start : Int,
                val expansion : Expansion?,
                val file_name : String,
                val is_primary : Boolean,
                val label : String?,
                val line_end : Int,
                val line_start : Int,
                //May be JsonNull
                val suggested_replacement : JsonElement,
                val text : List<Text>
            ) {

                //This can be used to get all children spans (e.g. in expansion) including
                //this one.
                fun getAllSpans() : List<Span> {
                    return listOf(this) +
                        (this.expansion?.def_site_span?.getAllSpans() ?: listOf()) +
                        (this.expansion?.span?.getAllSpans() ?: listOf())

                }

                //A mysterious field that is used with macros it seems
                //Unfortunately it may hold valid spans (and sometimes it
                //holds the only valid span of a message) so we have to
                //parse them.
                data class Expansion(
                    //May be null
                    val def_site_span : Span?,
                    val macro_decl_name : String,
                    val span : Span
                )

                data class Text(
                    val highlight_end : Int,
                    val highlight_start : Int,
                    val text : String
                )

            }

        }

        //file - The file to annotate
        fun doAnnotate(holder: AnnotationHolder)  {
            message.doAnnotate(holder, target)
        }
    }


}

data class TEAAnnotationInfo(val file: PsiFile, val editor: Editor)

class TEAAnnotationResult(commandOutput: String) {

    var parsedMessages = emptyList<CargoJson.TopMessage>()

    private companion object {
        val jsonParser : Gson =
            GsonBuilder()
                .registerTypeAdapter(CargoJson.TopMessage.Message::class.java,
                    CargoJson.TopMessage.Message.Companion.MessageFactory())
                .create()

    }

    init {

        parsedMessages += commandOutput.lines().filter {
            //Apparently some lines also have human readable messages. We ignore any lines not beginning with '{'
            val trimmed = it.trimStart()
            trimmed.isNotEmpty() && trimmed[0] =='{'
        }.filter {
            //We check the first field name by finding the first " then reading until another " (we expect
            //that a '{' present and don't check for it).
            val first = it.indexOfFirst { it == '"' }
            assert(first != -1)
            val second = it.indexOf('"', first + 1)
            assert(second != -1)

            when (it.subSequence(first + 1, second)) {
                "message" -> true
                "features" -> false
                else -> {assert(false); throw AssertionError()}
            }

        } .map {
            //Next parse the messages
            //We expect one message per line
            jsonParser.fromJson(it, CargoJson.TopMessage::class.java)
        }
    }

    fun prettyJson(): String {
        return "${jsonParser.toJson(parsedMessages)}\n"
    }

    override fun toString(): String {
        return prettyJson()
    }
}

class CargoCheckExtAnnotator: ExternalAnnotator<TEAAnnotationInfo, TEAAnnotationResult>() {

    //We use this server to cache the cargo check result instead of running it for every file
    object CargoCheckServer {

        //The cache map (project -> last result)
        private val cache = ConcurrentHashMap<Project, TEAAnnotationResult>()

        private fun doCargoCheck(project : Project) : TEAAnnotationResult {
            IJDebug.log("beginning cargo check run for project ${project.name}")

            val rustcExec : String = "/home/developer/.cargo/bin/cargo"
            val path = project.basePath

            val args = listOf(rustcExec, "check", "--manifest-path=$path/Cargo.toml", "--message-format=json")
            val cmd = args.joinToString(" ")

            IJDebug.log("executing command `$cmd`")

            val err : String
            val out : String
            val retVal : Int
            var process : Process? = null

            try {

                process = ProcessBuilder(args).start()

                //The streams have to be read together since they apparently block one
                //another somehow.
                data class NonblockReader(val input: InputStream) {
                    private val reader = input.bufferedReader()
                    var isDone = false
                    private var text = ""

                    //Try to read from the input stream without blocking
                    //Return true if anything was read, false otherwise
                    fun tryConsume(): Boolean {

                        if (!reader.ready()) return false

                        val line = reader.readLine()

                        if (line == null)
                            isDone = true
                        else
                            text += line + "\n"

                        return true
                    }

                    fun getText() = text

                }

                val errReader = NonblockReader(process.errorStream)
                val outReader = NonblockReader(process.inputStream)
                val sleepTime = 10L
                var totalSleepTime = 0L

                //val retVal = pb.waitFor()

                while (!errReader.isDone || !outReader.isDone) {

                    var inputRead = false
                    for (reader in listOf(errReader, outReader))
                        inputRead = inputRead || reader.tryConsume()

                    if (!inputRead) {

                        if (process.isAlive) {
                            //If no input and still running then wait a bit
                            process.waitFor(sleepTime, TimeUnit.MILLISECONDS)
                            totalSleepTime += sleepTime
                            assert(totalSleepTime < 10000)
                        } else {
                            //The output/error stream readers sometimes never stop trying to read - even
                            //after the process has closed.
                            //We have to figure out when the output/error stream is invalid ourselves
                            //by getting a hint from the caller for when the process has been
                            //closed and therefore more input can never be generated.
                            break
                        }

                    }

                }

                retVal = process.exitValue()

                err = errReader.getText()
                out = outReader.getText()
            } finally {
                //cleanup
                if (process != null)
                    process.destroy()
            }

            IJDebug.log("command rv = $retVal")
            IJDebug.log("command output = $out")
            IJDebug.log("command error = $err")

            val ret : TEAAnnotationResult

            when (retVal) {
            //There may be warnings so we always parse the messages
                0 -> ret = TEAAnnotationResult(out)
            //Compiler errors
                101 -> ret = TEAAnnotationResult(out)
            //Unrecognized return value
                else -> {
                    val msg =
                        """Unrecognized cargo return code $retVal.
                      |Command: `$cmd`
                    """.trimMargin()

                    throw RuntimeException(msg)
                }

            }

            IJDebug.log("ending cargo check for project ${project.name}")

            return ret
        }

        fun check(project : Project) : TEAAnnotationResult =
            cache.getOrPut(project, { doCargoCheck(project) })

        init {

            //Register a file listener to be notified on file changes.
            //We check if there were changes to any project files and

            ApplicationManager.getApplication().messageBus.connect().subscribe(VirtualFileManager.VFS_CHANGES,
                object : BulkFileListener {
                    override fun before(events: MutableList<out VFileEvent>) {
                    }

                    override fun after(events: MutableList<out VFileEvent>) {

                        //fun printEv(l : MutableList<VFileEvent>) : String =events.map { it.file }.joinToString(",")
                        IJDebug.log("files changed : $events}")

                        for (project in cache.keys) {
                            val relevantEvents = events.filterNotNull().filter{
                                ProjectFileIndex.getInstance(project).isInSourceContent(it.file!!)
                            }
                            if (relevantEvents.isNotEmpty()) {
                                IJDebug.log("found events for $project : $relevantEvents")

                                //Remove the result to force recalculation
                                cache.remove(project)
                            }

                        }
                    }
                })
        }

    }

    override fun collectInformation(file: PsiFile, editor: Editor, hasErrors: Boolean): TEAAnnotationInfo? {

        val ret : TEAAnnotationInfo?

        if (hasErrors) {
            //IJDebug.log("project already has errors, aborting cargo check")
            //ret = null
            ret = TEAAnnotationInfo(file, editor)
        } else {
            ret = TEAAnnotationInfo(file, editor)
        }

        return ret
    }

    override fun doAnnotate(info: TEAAnnotationInfo): TEAAnnotationResult? {
        return CargoCheckServer.check(info.file.project)
    }

    override fun apply(file: PsiFile, annotationResult: TEAAnnotationResult?, holder: AnnotationHolder) {

        if (annotationResult == null)
            return

        IJDebug.log("apply (file=${file.name}, sessionfile=${holder.currentAnnotationSession.file.name})")
        IJDebug.log("result = $annotationResult")

        annotationResult.parsedMessages.forEach {
            //the file to annotate is already embedded inside the annotation holder
            it.doAnnotate(holder)
        }

    }

}
