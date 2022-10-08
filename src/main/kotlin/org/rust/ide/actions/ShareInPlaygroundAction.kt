/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.actions

import com.google.common.annotations.VisibleForTesting
import com.google.gson.Gson
import com.intellij.execution.process.ProcessIOExecutorService
import com.intellij.ide.util.PropertiesComponent
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationListener
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.Messages
import com.intellij.util.io.HttpRequests
import com.intellij.util.net.ssl.CertificateManager
import com.intellij.util.proxy.CommonProxy
import org.jetbrains.annotations.TestOnly
import org.rust.RsBundle
import org.rust.ide.notifications.showBalloon
import org.rust.ide.utils.USER_AGENT
import org.rust.lang.core.psi.RsFile
import org.rust.openapiext.JsonUtils
import org.rust.openapiext.isUnitTestMode
import java.awt.datatransfer.StringSelection
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse.BodyHandlers
import java.time.Duration

class ShareInPlaygroundAction : DumbAwareAction() {

    override fun update(e: AnActionEvent) {
        val file = e.getData(CommonDataKeys.PSI_FILE) as? RsFile
        e.presentation.isEnabledAndVisible = file != null
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val file = e.getData(CommonDataKeys.PSI_FILE) as? RsFile ?: return
        val editor = e.getData(CommonDataKeys.EDITOR)

        val (text, hasSelection) = runReadAction {
            val selectedText = editor?.selectionModel?.selectedText
            if (selectedText != null) {
                selectedText to true
            } else {
                file.text to false
            }
        }

        val context = Context(file, text, hasSelection)
        performAction(project, context)
    }

    companion object {
        private const val SHOW_SHARE_IN_PLAYGROUND_CONFIRMATION = "rs.show.share.in.playground.confirmation"

        fun performAction(project: Project, context: Context) {
            val (file, text, hasSelection) = context
            if (!confirmShare(file, hasSelection)) return

            val channel = file.cargoProject?.rustcInfo?.version?.channel?.channel ?: "stable"
            val edition = file.crate.edition.presentation

            object : Task.Backgroundable(project, RsBundle.message("action.Rust.ShareInPlayground.progress.title")) {

                @Volatile
                private var gistId: String? = null

                override fun shouldStartInBackground(): Boolean = true
                override fun run(indicator: ProgressIndicator) {
                    val json = Gson().toJson(PlaygroundCode(text))
                    val request = HttpRequest.newBuilder()
                        .uri(URI.create("$playgroundHost/meta/gist/"))
                        .header("Content-Type", HttpRequests.JSON_CONTENT_TYPE)
                        .header("User-Agent", USER_AGENT)
                        .timeout(Duration.ofMillis(HttpRequests.READ_TIMEOUT.toLong()))
                        .POST(HttpRequest.BodyPublishers.ofString(json))
                        .build()

                    val client = createHttpClient()
                    val response = client.send(request, BodyHandlers.ofString())

                    gistId = JsonUtils.parseJsonObject(response.body()).getAsJsonPrimitive("id").asString
                }

                override fun onSuccess() {
                    val url = "https://play.rust-lang.org/?version=$channel&edition=$edition&gist=$gistId"
                    val copyUrlAction = NotificationAction.createSimple(RsBundle.message("action.Rust.ShareInPlayground.notification.copy.url.text")) {
                        CopyPasteManager.getInstance().setContents(StringSelection(url))
                    }
                    project.showBalloon(
                        RsBundle.message("action.Rust.ShareInPlayground.notification.title"),
                        RsBundle.message("action.Rust.ShareInPlayground.notification.text", url),
                        NotificationType.INFORMATION,
                        copyUrlAction,
                        NotificationListener.URL_OPENING_LISTENER
                    )
                }

                override fun onThrowable(error: Throwable) {
                    if (!isUnitTestMode) {
                        super.onThrowable(error)
                    }
                    project.showBalloon(
                        RsBundle.message("action.Rust.ShareInPlayground.notification.title"),
                        RsBundle.message("action.Rust.ShareInPlayground.notification.error"),
                        NotificationType.ERROR
                    )
                }
            }.queue()
        }

        private fun createHttpClient(): HttpClient = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(Duration.ofMillis(HttpRequests.CONNECTION_TIMEOUT.toLong()))
            .executor(ProcessIOExecutorService.INSTANCE)
            .sslContext(CertificateManager.getInstance().sslContext)
            .proxy(CommonProxy.getInstance())
            .build()

        private fun confirmShare(file: RsFile, hasSelection: Boolean): Boolean {
            val showConfirmation = PropertiesComponent.getInstance().getBoolean(SHOW_SHARE_IN_PLAYGROUND_CONFIRMATION, true)
            if (!showConfirmation) {
                return true
            }
            val doNotAskOption = object : DialogWrapper.DoNotAskOption.Adapter() {
                override fun rememberChoice(isSelected: Boolean, exitCode: Int) {
                    if (isSelected && exitCode == Messages.OK) {
                        PropertiesComponent.getInstance().setValue(SHOW_SHARE_IN_PLAYGROUND_CONFIRMATION, false, true)
                    }
                }
            }

            val message = if (hasSelection) {
                RsBundle.message("action.Rust.ShareInPlayground.confirmation.selected.text")
            } else {
                RsBundle.message("action.Rust.ShareInPlayground.confirmation", file.name)
            }

            val answer = Messages.showOkCancelDialog(
                message,
                RsBundle.message("action.Rust.ShareInPlayground.text"),
                Messages.getOkButton(),
                Messages.getCancelButton(),
                Messages.getQuestionIcon(),
                doNotAskOption
            )
            return answer == Messages.OK
        }
    }

    data class Context(val file: RsFile, val text: String, val hasSelection: Boolean)

    @VisibleForTesting
    data class PlaygroundCode(val code: String)
}

private var MOCK: String? = null

private val playgroundHost: String get() {
    return if (isUnitTestMode) {
        MOCK ?: error("Use `withMockPlaygroundHost`")
    } else {
        "https://play.rust-lang.org"
    }
}

@TestOnly
fun withMockPlaygroundHost(host: String, action: () -> Unit) {
    MOCK = host
    try {
        action()
    } finally {
        MOCK = null
    }
}
