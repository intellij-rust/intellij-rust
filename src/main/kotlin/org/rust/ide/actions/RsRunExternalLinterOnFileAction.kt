/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.actions

import com.intellij.codeInsight.actions.onSave.ActionOnSaveInfoBase
import com.intellij.codeInsight.daemon.impl.DaemonProgressIndicator
import com.intellij.ide.actionsOnSave.ActionOnSaveContext
import com.intellij.ide.actionsOnSave.ActionOnSaveInfo
import com.intellij.ide.actionsOnSave.ActionOnSaveInfoProvider
import com.intellij.ide.actionsOnSave.impl.ActionsOnSaveFileDocumentManagerListener
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.editor.Document
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import org.rust.cargo.runconfig.command.RunCargoCommandActionBase
import org.rust.ide.annotator.RsExternalLinterPassOnFile
import org.rust.openapiext.editor
import org.rust.openapiext.executeUnderProgress

class RsRunExternalLinterOnFileAction : RunCargoCommandActionBase() {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val editor = e.dataContext.editor ?: return
        val document = editor.document

        val task: Task.Backgroundable = CheckFileTask(project, document)
        ProgressManager.getInstance().run(task)
    }

    companion object {
        class CheckFileTask(project: Project, val document: Document): Task.ConditionalModal(project, "", false, ALWAYS_BACKGROUND) {
            override fun run(indicator: ProgressIndicator) {
                val pass = RsExternalLinterPassOnFile(project, document)

                executeUnderProgress(DaemonProgressIndicator()) {
                    runReadAction {
                        pass.collectInformation(indicator)
                        pass.doApplyInformationToEditor()
                    }
                }
            }

        }
    }
}

// It doesn't work as expected because it is called on any save (performed by IJ Platform)
// instead of being called only on explicit save (Ctrl+S)
class RsCheckFileOnSaveAction : ActionsOnSaveFileDocumentManagerListener.ActionOnSave() {
    override fun isEnabledForProject(project: Project): Boolean {
        return RsCheckFileOnSaveInfoProvider.isRunLinterOnSaveEnabled(project)
    }

    override fun processDocuments(
        project: Project,
        documents: Array<Document>
    ) {
        if (DumbService.isDumb(project)) return
        val document = documents.firstOrNull() ?: return
        val task = RsRunExternalLinterOnFileAction.Companion.CheckFileTask(project, document)
        ProgressManager.getInstance().run(task)
    }
}

class RsCheckFileOnSaveInfoProvider : ActionOnSaveInfoProvider() {
    override fun getActionOnSaveInfos(context: ActionOnSaveContext): MutableCollection<out ActionOnSaveInfo> {
        return mutableListOf<ActionOnSaveInfo>(
            object : ActionOnSaveInfoBase(context, "Rust linter", RUN_LINTER_ON_SAVE, false) {}
        )
    }

    companion object {
        fun isRunLinterOnSaveEnabled(project: Project): Boolean {
            return PropertiesComponent.getInstance(project).getBoolean(RUN_LINTER_ON_SAVE, RUN_LINTER_BY_DEFAULT)
        }

        private const val RUN_LINTER_ON_SAVE = "run.rust.linter.on.save"
        private const val RUN_LINTER_BY_DEFAULT = false
    }
}
