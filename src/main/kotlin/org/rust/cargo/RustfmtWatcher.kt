/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo

import com.intellij.execution.ExecutionException
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.components.serviceIfCreated
import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileEditor.FileDocumentManagerListener
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.guessProjectForFile
import com.intellij.util.DocumentUtil
import com.intellij.util.containers.ContainerUtil
import org.rust.cargo.project.model.CargoProject
import org.rust.cargo.project.model.cargoProjects
import org.rust.cargo.project.settings.rustfmtSettings
import org.rust.cargo.project.settings.toolchain
import org.rust.cargo.runconfig.command.workingDirectory
import org.rust.cargo.toolchain.tools.Rustfmt
import org.rust.cargo.toolchain.tools.Rustup.Companion.checkNeedInstallRustfmt
import org.rust.cargo.toolchain.tools.rustfmt
import org.rust.lang.core.psi.isNotRustFile
import org.rust.openapiext.checkIsDispatchThread
import org.rust.openapiext.virtualFile

@Service
class RustfmtWatcher {
    private val documentsToReformatLater: MutableSet<Document> = ContainerUtil.newConcurrentSet()
    private var isSuppressed: Boolean = false

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
        if (!project.rustfmtSettings.runRustfmtOnSave) return false
        return documentsToReformatLater.add(document)
    }

    class RustfmtListener : FileDocumentManagerListener {

        override fun beforeAllDocumentsSaving() {
            val documentsToReformatLater = getInstanceIfCreated()?.documentsToReformatLater
                ?: return
            val documentsToReformat = documentsToReformatLater.toList()
            documentsToReformatLater.clear()

            for ((cargoProject, documents) in documentsToReformat.groupBy(::findCargoProject)) {
                if (cargoProject == null) continue

                if (DumbService.isDumb(cargoProject.project)) {
                    documentsToReformatLater += documents
                } else {
                    reformatDocuments(cargoProject, documents)
                }
            }
        }

        override fun beforeDocumentSaving(document: Document) {
            val isSuppressed = getInstanceIfCreated()?.isSuppressed == true
            if (!isSuppressed) {
                val cargoProject = findCargoProject(document) ?: return
                if (DumbService.isDumb(cargoProject.project)) {
                    getInstance().reformatDocumentLater(document)
                } else {
                    reformatDocuments(cargoProject, listOf(document))
                }
            }
        }

        override fun unsavedDocumentsDropped() {
            getInstanceIfCreated()?.documentsToReformatLater?.clear()
        }
    }

    companion object {

        fun getInstance(): RustfmtWatcher = service()

        private fun getInstanceIfCreated(): RustfmtWatcher? = serviceIfCreated()

        private fun findCargoProject(document: Document): CargoProject? {
            val file = document.virtualFile ?: return null
            val project = guessProjectForFile(file) ?: return null
            return project.cargoProjects.findProjectForFile(file)
        }

        private fun reformatDocuments(cargoProject: CargoProject, documents: List<Document>) {
            val project = cargoProject.project
            if (!project.rustfmtSettings.runRustfmtOnSave) return
            val rustfmt = project.toolchain?.rustfmt() ?: return
            if (checkNeedInstallRustfmt(cargoProject.project, cargoProject.workingDirectory)) return
            documents.forEach { rustfmt.reformatDocument(cargoProject, it) }
        }

        @Throws(ExecutionException::class)
        private fun Rustfmt.reformatDocument(cargoProject: CargoProject, document: Document) {
            checkIsDispatchThread()
            if (!document.isWritable) return
            val formattedText = reformatDocumentTextOrNull(cargoProject, document) ?: return
            DocumentUtil.writeInRunUndoTransparentAction { document.setText(formattedText) }
        }
    }
}
