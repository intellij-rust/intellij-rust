/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.actions

import com.google.gson.Gson
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
import com.intellij.util.io.HttpRequests
import org.rust.RsBundle
import org.rust.cargo.project.workspace.CargoWorkspace
import org.rust.ide.notifications.showBalloon
import org.rust.ide.utils.USER_AGENT
import org.rust.lang.core.psi.RsFile
import org.rust.openapiext.JsonUtils
import java.awt.datatransfer.StringSelection

class ShareInPlaygroundAction : DumbAwareAction() {

    override fun update(e: AnActionEvent) {
        val file = e.getData(CommonDataKeys.PSI_FILE) as? RsFile
        e.presentation.isEnabledAndVisible = file != null
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val file = e.getData(CommonDataKeys.PSI_FILE) as? RsFile ?: return
        val editor = e.getData(CommonDataKeys.EDITOR)

        val text = runReadAction {
            editor?.selectionModel?.selectedText ?: file.text
        }

        val channel = file.cargoProject?.rustcInfo?.version?.channel?.channel ?: "stable"
        val edition = (file.crate?.edition ?: CargoWorkspace.Edition.EDITION_2018).presentation

        object : Task.Backgroundable(project, RsBundle.message("action.Rust.ShareInPlayground.progress.title")) {

            @Volatile
            private var gistId: String? = null

            override fun shouldStartInBackground(): Boolean = true
            override fun run(indicator: ProgressIndicator) {
                val json = Gson().toJson(PlaygroundCode(text))
                val response = HttpRequests.post("https://play.rust-lang.org/meta/gist/", "application/json")
                    .userAgent(USER_AGENT)
                    .connect {
                        it.write(json)
                        it.readString(indicator)
                    }

                gistId = JsonUtils.parseJsonObject(response).getAsJsonPrimitive("id").asString
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
                super.onThrowable(error)
                project.showBalloon(
                    RsBundle.message("action.Rust.ShareInPlayground.notification.title"),
                    RsBundle.message("action.Rust.ShareInPlayground.notification.error"),
                    NotificationType.ERROR
                )
            }
        }.queue()
    }

    private data class PlaygroundCode(val code: String)
}
