package org.rust.ide.annotator

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
import com.fasterxml.jackson.databind.node.NullNode
import com.intellij.openapi.util.TextRange

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

    protected fun finalize() {
        globalMap.remove(fullId)
    }

    override fun toString(): String {
        return "<a href=\"$fullId\">$name</a>"
    }
}

open class Logger(val notificationGroup : NotificationGroup) {

    var prefix = Stack<String>()
    var defaultNotificationType = NotificationType.INFORMATION
    var defaultTitle = ""
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
            var maxWid = 500
            var maxHeight = 500
            var max = Dimension(maxWid,maxHeight)

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

                var scrollPane = JScrollPane(editor)
                scrollPane.verticalScrollBarPolicy = JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED
                scrollPane.maximumSize = Dimension(500,500)

                finalComponent = scrollPane
            }

            if (SCP) {
                var scrollPane = JScrollPane(finalComponent)
                scrollPane.verticalScrollBarPolicy = JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED
                scrollPane.maximumSize = max
                //scrollPane.preferredSize = Dimension(500,500)

                var panel = JPanel()
                panel.add(scrollPane)
                panel.layout = BoxLayout(panel, BoxLayout.PAGE_AXIS)
                //panel.preferredSize = Dimension(500,500)
                //panel.maximumSize = Dimension(500,500)


                finalComponent = panel
            }

            var popup = false

            if (popup) {
                val pos = MouseInfo.getPointerInfo().location
                var builder = JBPopupFactory.getInstance().createBalloonBuilder(finalComponent)
                //var builder = JBPopupFactory.getInstance().createBalloonBuilder(scrollPane).setAnimationCycle(100)
                //var builder = JBPopupFactory.getInstance().createBalloonBuilder(editor).setAnimationCycle(100)
                //var builder = JBPopupFactory.getInstance().createBalloonBuilder(panel).setAnimationCycle(100)
                //var builder = JBPopupFactory.getInstance().createDialogBalloonBuilder(editor, info.name)
                //var builder = JBPopupFactory.getInstance().createDialogBalloonBuilder(scrollPane, info.name)
                //var builder = JBPopupFactory.getInstance().createHtmlTextBalloonBuilder(info.infoString, MessageType.INFO, null)
                builder.setAnimationCycle(100)
                builder.createBalloon()
                var balloon = builder.createBalloon()

                println("ball prefsz=${balloon.preferredSize}")

                //First calculate the best direction for the popup
                balloon.show(RelativePoint(pos), Balloon.Position.atLeft)
            } else {
                if (false) {
                    var frame = JFrame()

                    frame.add(finalComponent)
                    frame.maximumSize = Dimension(500, 500)
                    frame.pack()
                    frame.maximumSize = Dimension(500, 500)
                    frame.setSize(frame.maximumSize)
                    frame.isVisible = true
                }

                if (true) {
                    val pos = MouseInfo.getPointerInfo().location

                    var builder = JBPopupFactory.getInstance().createComponentPopupBuilder(finalComponent, null)

                    var pop = builder.createPopup()

                    pop.show(RelativePoint(pos))

                    pop.moveToFitScreen()
                }
            }


        }

    }

    fun log(content : String,
            title : String = defaultTitle,
            notificationType: NotificationType = defaultNotificationType,
            notificationListener: NotificationListener? = ExtraInfoListener) {

        if (doLog)
            notificationGroup.createNotification(title, prefix.joinToString("") + content, notificationType, notificationListener).notify(null)
    }

    fun log(info : EI,
            title : String = defaultTitle,
            notificationType: NotificationType = defaultNotificationType,
            notificationListener: NotificationListener? = ExtraInfoListener) {

        if (doLog)
            notificationGroup.createNotification(title, prefix.joinToString("") + info.toString(), notificationType, notificationListener).notify(null)
    }

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

        var ctxStr = if (forContext != "") "for $forContext " else ""
        var err = "Unexpected $name `$actual` $ctxStr(expected `$expected`)"


        throw RuntimeException(err)
    }

    //A top level compiler message
    data class TopMessage(
        val message: Message,
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

            override fun doAnnotate(holder: AnnotationHolder) {
                //We don't expect these to be called directly.
                //We only expect help messages as children of error messages

            }
        }

        class HelpMessage(message: String, spans: List<Span>) : InfoMessageBase(message, spans)
        class NoteMessage(message: String, spans: List<Span>) : InfoMessageBase(message, spans)

        class ErrorMessage(val children: List<Message>, val code: Code?, override val message: String, override val spans: List<Span>) : Message() {
            init {
                children.forEach { assert(it is NoteMessage || it is HelpMessage) }
            }

            override fun doAnnotate(holder: AnnotationHolder) {
                DEBUG.beginLog("ErrorMessage.doAnnotate")

                //Each span has a label and a text range
                //The error itself is relevant for all of them.
                //So first we create the "extra error info" that will be displayed below the span label
                //and then we use it for all spans.

                //Get all children (help/note) messages without spans
                val childrenMsg = children.filter{ it.spans.isEmpty() }.map { (if (it is NoteMessage) "Note: " else "Help: ") + it.message }.joinToString("<br/>")
                val extraMsg = "$message ${if (code != null) "(${code.formatAsLink()})" else ""}<br/>$childrenMsg"

                //Generate annotations for all spans of this error message plus of the children messages with spans
                val allSpans = spans + children.filter {it.spans.isNotEmpty()}.map {it.spans}.flatten()

                allSpans.forEach { it.doAnnotate(holder, message, extraMsg) }

                DEBUG.endLog()

            }
        }
        class AbortingDueToPreviousErrorsMessage(override val message : String) : Message() {
            override val spans = emptyList<Span>()
            override fun doAnnotate(holder: AnnotationHolder) {
                //Nothing to do
            }
        }

        abstract class Message {

            abstract val message : String
            abstract val spans : List<Span>

            abstract fun doAnnotate(holder: AnnotationHolder)

            companion object {

                @JsonCreator
                @JvmStatic
                fun factory(
                    children: List<Message>,
                    code: Code?,
                    level: String,
                    message: String,
                    rendered: NullNode,
                    spans: List<Message.Span>
                ): Message{
                    var msgType = level
                    if (level == "error")
                        msgType = "abort due to previous errors"

                    val ctx = "a message of type `$msgType`"

                    //assert(rendered == null)
                    /*
                    if (rendered != null)
                        throw RuntimeException("Unrecognized value for \"rendered\" field (`${rendered}`) in rust compiler message ")
                        */
                    if (level != "error") {
                        //These are checks for degenerate messages - i.e. messages with only a message string.
                        //The types are: note, help, (error && code==null)

                        checkExp("code", code, null, ctx)
                        checkExp("children size", children.size, 0, ctx)

                    }

                    when (level) {
                        "error" -> {

                            //Apparently errors *can* have null codes. At this point it is unclear if
                            //it is even possible to distinguish between abort errors and regular ones.
                            if (code == null && spans.isEmpty()) {
                                //Message is "aborting due to previous errors"
                                checkExp("code", code, null, ctx)
                                checkExp("children size", children.size, 0, ctx)
                                checkExp("spans size", spans.size, 0, ctx)
                                return AbortingDueToPreviousErrorsMessage(message)
                            }

                            var err = ""
                            if (spans.size == 0) {
                                err = "span count (expected >0, got ${spans.size})"
                            }

                            //Children may be any number (0 or bigger)
                            //code may be null

                            if (err != "")
                                throw RuntimeException("Error - Unexpected $err for an error message.")

                            return ErrorMessage(children, code, message, spans)
                        }
                        "help" -> return HelpMessage(message, spans)
                        "note" -> return NoteMessage(message, spans)

                        else -> throw RuntimeException("Unrecognized level `${level}` in rust compiler message")
                    }
                }
            }

            //An error code (?)
            data class Code(val code : String, val explanation: String) {

                fun formatAsLink() : String {
                    val rustErrorIndexPrefix = "https://doc.rust-lang.org/error-index.html"

                    return "Code: <a href=\"$rustErrorIndexPrefix#$code\">$code</a>"
                }
            }

            //Code spans (?)
            data class Span(
                val byte_end : Int,
                val byte_start : Int,
                val column_end : Int,
                val column_start : Int,
                val expansion : JsonNode?,
                val file_name : String,
                val is_primary : Boolean,
                val label : String?,
                val line_end : Int,
                val line_start : Int,
                val suggested_replacement : JsonNode?,
                val text : List<Text>
            ) {
                init {
                    //Make sure that label is only null if is_primary is on
                    assert(label != null || is_primary)
                }

                data class Text(
                    val highlight_end : Int,
                    val highlight_start : Int,
                    val text : String
                ) {}

                //errMsg - The original error message (used if label is null)
                //extra - The extra info
                fun doAnnotate(holder : AnnotationHolder, errMsg: String, extra : String) {
                    DEBUG.beginLog("Span.doAnnotate")

                    val text = TextRange(byte_start, byte_end)
                    val short = label ?: errMsg
                    val full = "$short<hr>$extra"

                    DEBUG.log("adding error (short=$short, ${EI("full", full)})")
                    val annot = holder.createErrorAnnotation(text, short)
                    annot.tooltip = full

                    DEBUG.endLog()
                }

            }

        }

        data class Target(val kind: List<String>, val name : String, val src_path : String) {}

        //file - The file to annotate
        fun doAnnotate(file: PsiFile, holder: AnnotationHolder)  {
            DEBUG.beginLog("doAnnotate(file=${file.virtualFile.path},src_path=${target.src_path}")
            if (target.src_path != file.virtualFile.path) {
                DEBUG.endLog()
                return
            }

            message.doAnnotate(holder)

            DEBUG.endLog()
        }
    }


}

data class TEAAnnotationInfo(val file: PsiFile, val editor: Editor) {

}

class TEAAnnotationResult(val commandOutput: String) {

    private companion object {
        val mapper = jacksonObjectMapper()

    }

    val parsedMessages : List<CargoJson.TopMessage> = mapper.readerFor(CargoJson.TopMessage::class.java).readValues<CargoJson.TopMessage>(commandOutput).readAll()

    fun prettyJson(): String {
        var res = ""

        for (msg in parsedMessages) {
            res += "json=${mapper.writerWithDefaultPrettyPrinter().writeValueAsString(msg)}\n"
        }

        return res

    }

    override fun toString(): String {
        return prettyJson()
    }
}

class CargoCheckExtAnnotator: ExternalAnnotator<TEAAnnotationInfo, TEAAnnotationResult>() {


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

        val rustcExec : String = "/home/developer/.cargo/bin/cargo"
        val path = info.editor.project?.basePath

        var args = arrayOf(rustcExec, "check", "--manifest-path=$path/Cargo.toml", "--message-format=json")
        var cmd = args.joinToString(" ")

        DEBUG.log("executing ${EI("cargo check", "$cmd")}")
        var ps = Runtime.getRuntime().exec(args)

        val err = ps.errorStream.bufferedReader().use { it.readText() }
        val out = ps.inputStream.bufferedReader().use { it.readText() }
        val retVal = ps.waitFor()

        DEBUG.log("cargo check returned $retVal, ${EI("output", out)}, ${EI("error", err)} (" +
            "${EI("output-json", out, JsonFileType.INSTANCE)}, ${EI("err-json", err, JsonFileType.INSTANCE)}")

        var ret : TEAAnnotationResult?

        when (retVal) {
            //No errors
            0 -> ret = null
            //Compiler errors
            101 -> ret = TEAAnnotationResult(out)
            //Unrecognized return value
            else -> {
                var msg =
                    """Unrecognized cargo return code $retVal.
                      |Command: `$cmd`
                    """.trimMargin()

                throw RuntimeException(msg)
            }

        }

        DEBUG.log("done, annotation result ret is ${EI("ret", ret.toString())}")

        DEBUG.endLog()

        return ret
    }

    override fun apply(file: PsiFile, annotationResult: TEAAnnotationResult?, holder: AnnotationHolder) {

        if (annotationResult == null)
            return

        DEBUG.beginLog("apply(${file.virtualFile.path})")

        DEBUG.log("got annotation result ${EI("json", annotationResult!!.prettyJson())}")

        annotationResult!!.parsedMessages.withIndex().forEach { pair ->
            val i = pair.index
            val msg = pair.value

            DEBUG.log("doing message $i")
            msg.doAnnotate(file, holder)

        }

        DEBUG.endLog()
    }

}

