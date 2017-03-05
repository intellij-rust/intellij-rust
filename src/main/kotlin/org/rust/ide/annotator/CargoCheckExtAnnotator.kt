package org.rust.ide.annotator
import com.intellij.openapi.diagnostic.Logger as IJLogger
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

import java.awt.MouseInfo

import javax.swing.event.HyperlinkEvent

import com.intellij.lang.annotation.*
import com.intellij.openapi.editor.*
import com.intellij.psi.PsiFile
import com.intellij.json.JsonFileType
import com.intellij.notification.*
import com.intellij.openapi.fileTypes.*
import com.intellij.openapi.ui.popup.*
import com.intellij.ui.EditorTextField
import com.intellij.ui.awt.RelativePoint

import com.fasterxml.jackson.module.kotlin.*
import com.fasterxml.jackson.databind.JsonNode
import java.awt.Dimension
import javax.swing.*
import com.fasterxml.jackson.annotation.*
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import java.io.InputStream

import kotlin.collections.*

//ExtraInfo
class EI(val name : String, val infoString: String, val fileType : FileType = FileTypes.PLAIN_TEXT) {

    companion object {
        var lastId = AtomicInteger()
        val globalMap = ConcurrentHashMap<String, EI>()
    }

    var id = lastId.andIncrement
    var fullId = "#$id"

    init {
        globalMap[fullId] = this
    }

    @Suppress("ProtectedInFinal", "unused")
    protected fun finalize() {
        globalMap.remove(fullId)
    }

    override fun toString(): String {
        return "<a href=\"$fullId\">$name</a>"
    }
}

open class Logger(@Suppress("unused") val notificationGroup : NotificationGroup) {

    var prefix = Stack<String>()
    var defaultNotificationType = NotificationType.INFORMATION
    var defaultTitle = "CARGO-CHECK"
    var doLog = true

    object ExtraInfoListener : com.intellij.notification.NotificationListener.Adapter() {
        override fun hyperlinkActivated(notification: Notification, e: HyperlinkEvent) {

            val id = e.description


            val info: EI = EI.globalMap[id] ?: throw RuntimeException("Unrecognized extra infoString id $id.\n" +
                "Recognized ids are: ${EI.globalMap.keys.joinToString()}")

            //Use Intellij Editor
            val IJ = true
            //Use wrapping scroll pane
            val SCP = false
            var finalComponent : JComponent
            val maxWid = 500
            val maxHeight = 500
            val max = Dimension(maxWid,maxHeight)

            if (IJ) {
                val doc = EditorFactory.getInstance().createDocument(info.infoString)
                val editor = EditorTextField(doc, null, info.fileType, true, false)

                editor.addSettingsProvider { x ->
                    if (x.component.preferredSize.width > maxWid) {
                        editor.preferredSize = max
                        x.setVerticalScrollbarVisible(true)
                    }

                    x.settings.isUseSoftWraps = true

                }

                finalComponent = editor
            } else {
                val editor = JTextArea()
                editor.text = info.infoString
                println(editor.preferredSize)
                println(editor.preferredScrollableViewportSize)
                editor.lineWrap = true
                //editor.columns = 50
                println(editor.preferredSize)
                //editor.rows = 30
                //editor.maximumSize = Dimension(500,500)
                println(editor.preferredSize)

                val scrollPane = JScrollPane(editor)
                scrollPane.verticalScrollBarPolicy = JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED
                scrollPane.maximumSize = Dimension(500,500)

                finalComponent = scrollPane
            }

            if (SCP) {
                val scrollPane = JScrollPane(finalComponent)
                scrollPane.verticalScrollBarPolicy = JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED
                scrollPane.maximumSize = max
                //scrollPane.preferredSize = Dimension(500,500)

                val panel = JPanel()
                panel.add(scrollPane)
                panel.layout = BoxLayout(panel, BoxLayout.PAGE_AXIS)
                //panel.preferredSize = Dimension(500,500)
                //panel.maximumSize = Dimension(500,500)


                finalComponent = panel
            }

            val popup = false

            if (popup) {
                val pos = MouseInfo.getPointerInfo().location
                val builder = JBPopupFactory.getInstance().createBalloonBuilder(finalComponent)
                //var builder = JBPopupFactory.getInstance().createBalloonBuilder(scrollPane).setAnimationCycle(100)
                //var builder = JBPopupFactory.getInstance().createBalloonBuilder(editor).setAnimationCycle(100)
                //var builder = JBPopupFactory.getInstance().createBalloonBuilder(panel).setAnimationCycle(100)
                //var builder = JBPopupFactory.getInstance().createDialogBalloonBuilder(editor, info.name)
                //var builder = JBPopupFactory.getInstance().createDialogBalloonBuilder(scrollPane, info.name)
                //var builder = JBPopupFactory.getInstance().createHtmlTextBalloonBuilder(info.infoString, MessageType.INFO, null)
                builder.setAnimationCycle(100)
                builder.createBalloon()
                val balloon = builder.createBalloon()

                println("ball prefsz=${balloon.preferredSize}")

                //First calculate the best direction for the popup
                balloon.show(RelativePoint(pos), Balloon.Position.atLeft)
            } else {
                if (false) {
                    val frame = JFrame()

                    frame.add(finalComponent)
                    frame.maximumSize = Dimension(500, 500)
                    frame.pack()
                    frame.maximumSize = Dimension(500, 500)
                    frame.size = frame.maximumSize
                    frame.isVisible = true
                }

                if (true) {
                    val pos = MouseInfo.getPointerInfo().location

                    val builder = JBPopupFactory.getInstance().createComponentPopupBuilder(finalComponent, null)

                    val pop = builder.createPopup()

                    pop.show(RelativePoint(pos))

                    pop.moveToFitScreen()
                }
            }


        }

    }

    fun log(content : String,
            title : String = defaultTitle,
            @Suppress("UNUSED_PARAMETER") notificationType: NotificationType = defaultNotificationType,
            @Suppress("UNUSED_PARAMETER") notificationListener: NotificationListener? = ExtraInfoListener) {

        if (doLog) {
            //notificationGroup.createNotification(title, prefix.joinToString("") + content, notificationType, notificationListener).notify(null)
            IJLogger.getInstance(title).debug("${prefix.joinToString("")}: $content")
        }

    }

    fun log(info : EI,
            title : String = defaultTitle,
            notificationType: NotificationType = defaultNotificationType,
            notificationListener: NotificationListener? = ExtraInfoListener) =
        //log(info.toString(), title, notificationType, notificationListener)
        log("${info.name}: ${info.infoString}", title, notificationType, notificationListener)

    fun beginLog(name : String) {
        prefix.push(name + ": ")
        log("begin")
    }

    fun endLog() {
        log("end")
        prefix.pop()
    }

}

object DEBUG : Logger(NotificationGroup("RustCheck", NotificationDisplayType.NONE, true)) {
    var debugEnabled = true

    init {
        prefix.push("Debug: ")
        doLog = debugEnabled
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
        val features : JsonNode,
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

        //FIXME: Make a serialize for messages that also serializes the level
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
            //FIXME: Change level to an enum
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
                DEBUG.beginLog("ErrorMessage.doAnnotate")

                holder.currentAnnotationSession.file
                if (canSkipAnnotation()) {
                    DEBUG.log("skipping annotation for error $message")
                    DEBUG.endLog()
                    return
                }

                //Next we do the span annotations
                val annotFile = holder.currentAnnotationSession.file.virtualFile.path
                val severity = when (level) {
                    "error" -> HighlightSeverity.ERROR
                    "warning" -> HighlightSeverity.WEAK_WARNING
                    else -> { throw AssertionError() }
                }
                //Can't have hyperlinks in the info. We'll include it in the tooltip instead.
                val codeStr = if (code != null) "Code: ${code.formatAsLink()}" else ""
                val shortErrorStr = message

                //If spans are empty we add a "global" error
                if (spans.isEmpty()) {
                    DEBUG.log("global error")
                    if (target.src_path != annotFile) {
                        DEBUG.log("not main file, skipping")
                    } else {
                        DEBUG.log("doing error $shortErrorStr")
                        val annot = holder.createAnnotation(severity, TextRange.EMPTY_RANGE, shortErrorStr)

                        annot.isFileLevelAnnotation = true
                        annot.setNeedsUpdateOnTyping(true)
                        annot.tooltip = message + if(codeStr != "") "<hr/>$codeStr" else ""

                    }
                    DEBUG.endLog()
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
                val childrenMsg = children.map { (if (it is NoteMessage) "Note: " else "Help: ") + it.message }.joinToString("<br/>")

                //Create a problem group to group all annotations
                val problemGroup = ProblemGroup { shortErrorStr }

                //Generate annotations for all spans of this error message plus of the children messages with spans
                val allSpans = (listOf(this) + children).map {it.getAllSpans()}.flatten()

                DEBUG.log("doing annotations for file $annotFile (${allSpans.size} spans)")
                allSpans.filter {
                    //First filter all spans not relevant for this file
                    val res = annotFile == it.file_name
                    if (!res)
                        DEBUG.log("skipping span for file ${it.file_name}.")
                    res
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
                        (if (it.is_primary && it.label != null) "${it.label}<br/>" else "") +
                        childrenMsg
                    val tooltip = short + if (extra.isBlank()) "" else "<hr/>" + extra

                    DEBUG.log("adding annotation (short=$short, ${EI("tooltip", tooltip)})")
                    val spanSeverity = if (it.is_primary) severity else HighlightSeverity.WEAK_WARNING
                    val annot = holder.createAnnotation(spanSeverity, textRange, short)

                    annot!!.tooltip = tooltip
                    annot.problemGroup = problemGroup
                    annot.setNeedsUpdateOnTyping(true)
                }

                DEBUG.endLog()

            }
        }

        class ErrorMessage(children: List<Message>, code: Code?, message: String, spans: List<Span>) :
            ErrorBaseMessage(children, code, message, spans, "error")
        class WarningMessage(children: List<Message>, code: Code?, message: String, spans: List<Span>) :
            ErrorBaseMessage(children, code, message, spans, "warning")

        abstract class Message {

            abstract val message : String
            abstract val spans : List<Span>

            //Return all the spans of this message including their child spans
            fun getAllSpans() : List<Span> {
                return spans.map{it.getAllSpans()}.flatten()
            }

            abstract fun doAnnotate(holder: AnnotationHolder, target: Target)

            companion object {

                @JsonCreator
                @JvmStatic
                fun factory(
                    children: List<Message>,
                    code: Code?,
                    level: String,
                    message: String,
                    //It is unclear what this is but it may be a string.
                    rendered: String?,
                    spans: List<Message.Span>
                ): Message{
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
                val suggested_replacement : JsonNode?,
                val text : List<Text>
            ) {

                //This can be used to get all children spans (e.g. in expansion) including
                //this one.
                @JsonIgnore
                fun getAllSpans() : List<Span> {
                    println("getallspans, dss = ${this.expansion?.def_site_span}")

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
        fun doAnnotate(file: PsiFile, holder: AnnotationHolder)  {
            //Apparently src_path is not important - stays the same for different files.
/*
            if (target.src_path != file.virtualFile.path) {
                DEBUG.endLog()
                return
            }
            */

            DEBUG.beginLog("doAnnotate(file=${file.virtualFile.path})")

            message.doAnnotate(holder, target)

            DEBUG.endLog()
        }
    }


}

data class TEAAnnotationInfo(val file: PsiFile, val editor: Editor)

class TEAAnnotationResult(commandOutput: String) {

    var parsedMessages = emptyList<CargoJson.TopMessage>()

    private companion object {
        val mapper = jacksonObjectMapper()

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
            mapper.readerFor(CargoJson.TopMessage::class.java).readValue<CargoJson.TopMessage>(it)
        }
    }

    fun prettyJson(): String {
        return "${mapper.writerWithDefaultPrettyPrinter().writeValueAsString(parsedMessages)}\n"
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
            DEBUG.beginLog("doCargoCheck(${project.name})")

            val rustcExec : String = "/home/developer/.cargo/bin/cargo"
            val path = project.basePath

            val args = listOf(rustcExec, "check", "--manifest-path=$path/Cargo.toml", "--message-format=json")
            val cmd = args.joinToString(" ")

            DEBUG.log("executing ${EI("cargo check", cmd)}")

            val pb = ProcessBuilder(args).start()
            //val ps = Runtime.getRuntime().exec(args)

            DEBUG.log("after exec")

            DEBUG.log("getting error/std output")

            //The streams have to be read together since they apparently block one
            //another somehow.
            data class NonblockReader(val input : InputStream) {
                private val reader = input.bufferedReader()
                var isDone = false
                private var text = ""

                //Try to read from the input stream without blocking
                //Return true if anything was read, false otherwise
                fun tryConsume() : Boolean {

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

            val errReader = NonblockReader(pb.errorStream)
            val outReader = NonblockReader(pb.inputStream)
            val sleepTime = 1L
            var totalSleepTime = 0L

            while (!errReader.isDone || !outReader.isDone) {
                var inputRead = false
                for (reader in listOf(errReader, outReader))
                    inputRead = inputRead || reader.tryConsume()

                //If no input read then sleep a bit
                if (!inputRead) {
                    Thread.sleep(sleepTime)

                    totalSleepTime += sleepTime
                    //Make sure we aren't stuck
                    //assert(totalSleepTime < 1000)
                    if (totalSleepTime > 1000)
                        break
                }

            }

            val err = errReader.getText()
            DEBUG.log("getting std output")
            val out = outReader.getText()
            DEBUG.log("waiting for retval")
            val retVal = pb.waitFor()

            DEBUG.log("cargo check returned $retVal, ${EI("output", out)}, ${EI("error", err)} (" +
                "${EI("output-json", out, JsonFileType.INSTANCE)}, ${EI("err-json", err, JsonFileType.INSTANCE)}")

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

            DEBUG.endLog()

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
                        DEBUG.beginLog("files changed handler (before)")
                        DEBUG.log("events : $events")
                        DEBUG.endLog()
                    }

                    override fun after(events: MutableList<out VFileEvent>) {
                        DEBUG.beginLog("files changed handler (after)")

                        //fun printEv(l : MutableList<VFileEvent>) : String =events.map { it.file }.joinToString(",")
                        DEBUG.log("files changed : $events}")

                        for (project in cache.keys) {
                            val relevantEvents = events.filterNotNull().filter{
                                ProjectFileIndex.getInstance(project).isInSourceContent(it.file!!)
                            }
                            if (relevantEvents.isNotEmpty()) {
                                DEBUG.log("found events for $project : $relevantEvents")

                                //Remove the result to force recalculation
                                cache.remove(project)
                            }

                        }

                        DEBUG.endLog()
                    }
                })
        }

    }

    override fun collectInformation(file: PsiFile, editor: Editor, hasErrors: Boolean): TEAAnnotationInfo? {

        val ret : TEAAnnotationInfo?
        DEBUG.beginLog("collectInformation(${file.name})")

        if (hasErrors) {
            DEBUG.log("file already has errors, aborting cargo check")
            ret = null
        } else {
            DEBUG.log("no errors, will do cargo check")
            ret = TEAAnnotationInfo(file, editor)
        }

        DEBUG.endLog()

        return ret
    }

    override fun doAnnotate(info: TEAAnnotationInfo): TEAAnnotationResult? {

        DEBUG.beginLog("doAnnotate(${info.file.name})")

        val ret = CargoCheckServer.check(info.file.project)

        DEBUG.log("done, annotation result ret is ${EI("ret", ret.toString())}")

        DEBUG.endLog()

        return ret
    }

    override fun apply(file: PsiFile, annotationResult: TEAAnnotationResult?, holder: AnnotationHolder) {

        if (annotationResult == null)
            return

        DEBUG.beginLog("apply(file=${file.virtualFile.path},annotationSession.file=${holder.currentAnnotationSession.file.virtualFile.path})")

        DEBUG.log("got annotation result ${EI("json", annotationResult.prettyJson())}")

        annotationResult.parsedMessages.withIndex().forEach { pair ->
            val i = pair.index
            val msg = pair.value

            DEBUG.log("doing message $i")
            msg.doAnnotate(file, holder)

        }

        DEBUG.endLog()
    }

}

