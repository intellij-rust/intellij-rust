/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

// BACKCOMPAT: 2019.3
@file:Suppress("DEPRECATION")

package org.rust.cargo

import com.intellij.AppTopics
import com.intellij.execution.ExecutionException
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.BaseComponent
import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileEditor.FileDocumentManagerListener
import com.intellij.openapi.project.guessProjectForFile
import com.intellij.util.DocumentUtil
import com.intellij.util.containers.ContainerUtil
import org.rust.cargo.project.model.CargoProject
import org.rust.cargo.project.model.cargoProjects
import org.rust.cargo.project.settings.rustSettings
import org.rust.cargo.project.settings.toolchain
import org.rust.cargo.runconfig.command.workingDirectory
import org.rust.cargo.toolchain.Rustfmt
import org.rust.cargo.toolchain.Rustup.Companion.checkNeedInstallRustfmt
import org.rust.lang.core.psi.isNotRustFile
import org.rust.openapiext.checkIsDispatchThread
import org.rust.openapiext.virtualFile

class RustfmtWatcher : BaseComponent {
    private val documentsToReformatLater: MutableSet<Document> = ContainerUtil.newConcurrentSet()
    private var isSuppressed: Boolean = false

    override fun initComponent() {
        ApplicationManager.getApplication()
            .messageBus
            .connect()
            .subscribe(AppTopics.FILE_DOCUMENT_SYNC, RustfmtListener())
    }

    fun withoutReformatting(action: () -> Unit) {
        val oldStatus = isSuppressed
        try {
            isSuppressed = true
            action()
        } finally {
            isSuppressed = oldStatus
        }
    }

    fun reformatDocumentLater(document: Document): Boolean {
        val file = document.virtualFile ?: return false
        if (file.isNotRustFile) return false
        val project = guessProjectForFile(file) ?: return false
        if (!project.rustSettings.runRustfmtOnSave) return false
        return documentsToReformatLater.add(document)
    }

    private inner class RustfmtListener : FileDocumentManagerListener {

        override fun beforeAllDocumentsSaving() {
            val documentsToReformat = HashSet(documentsToReformatLater)
            documentsToReformatLater.clear()
            documentsToReformat.groupBy(::findCargoProject).forEach(::reformatDocuments)
        }

        override fun beforeDocumentSaving(document: Document) {
            if (!isSuppressed) {
                reformatDocuments(findCargoProject(document), listOf(document))
            }
        }

        override fun unsavedDocumentsDropped() {
            documentsToReformatLater.clear()
        }
    }

    companion object {

        fun getInstance(): RustfmtWatcher =
            ApplicationManager.getApplication().getComponent(RustfmtWatcher::class.java)

        private fun findCargoProject(document: Document): CargoProject? {
            val file = document.virtualFile ?: return null
            val project = guessProjectForFile(file) ?: return null
            return project.cargoProjects.findProjectForFile(file)
        }

        private fun reformatDocuments(cargoProject: CargoProject?, documents: List<Document>) {
            if (cargoProject == null) return
            val project = cargoProject.project
            if (!project.rustSettings.runRustfmtOnSave) return
            val rustfmt = project.toolchain?.rustfmt() ?: return
            if (checkNeedInstallRustfmt(cargoProject.project, cargoProject.workingDirectory)) return
            documents.forEach { rustfmt.reformatDocument(cargoProject, it) }
        }

        @Throws(ExecutionException::class)
        private fun Rustfmt.reformatDocument(cargoProject: CargoProject, document: Document) {
            checkIsDispatchThread()
            if (!document.isWritable) return
            val formattedText = reformatDocumentText(cargoProject, document) ?: return
            DocumentUtil.writeInRunUndoTransparentAction { document.setText(formattedText) }
        }
    }
}
