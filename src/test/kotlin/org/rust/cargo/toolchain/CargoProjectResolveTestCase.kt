package org.rust.cargo.toolchain

import com.google.common.util.concurrent.SettableFuture
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiReference
import org.assertj.core.api.Assertions.assertThat
import org.rust.cargo.RustWithToolchainTestCaseBase
import org.rust.cargo.project.settings.toolchain
import org.rust.cargo.project.workspace.CargoProjectWorkspace
import org.rust.cargo.project.workspace.CargoProjectWorkspaceListener
import org.rust.cargo.project.workspace.CargoProjectWorkspaceListener.UpdateResult
import org.rust.cargo.util.getComponentOrThrow
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit

class CargoProjectResolveTestCase : RustWithToolchainTestCaseBase() {

    override val dataPath: String = "src/test/resources/org/rust/cargo/toolchain/fixtures"

    private val TIMEOUT: Long = 10 * 1000 /* millis */

    fun testResolveExternalLibrary() = withProject("external_library") {
        val f = bindToProjectUpdateEvent() {
            val reference = extractReference("src/main.rs")
            reference.resolve()
        }

        updateCargoProject()

        assertThat(f.get(TIMEOUT, TimeUnit.MILLISECONDS)).isNotNull()
    }

    fun testResolveLocalPackage() = withProject("local_package") {
        val f = bindToProjectUpdateEvent() {
            val reference = extractReference("src/main.rs")
            reference.resolve()
        }

        updateCargoProject()

        assertThat(f.get(TIMEOUT, TimeUnit.MILLISECONDS)).isNotNull()
    }

    fun testResolveLocalPackageMod() = withProject("local_package_mod") {
        val f = bindToProjectUpdateEvent() {
            val reference = extractReference("src/bar.rs")
            reference.resolve()
        }

        updateCargoProject()

        assertThat(f.get(TIMEOUT, TimeUnit.MILLISECONDS)).isNotNull()
    }

    fun testModuleRelations() = withProject("mods") {
        val f = bindToProjectUpdateEvent {
            val reference = extractReference("src/foo.rs")
            reference.resolve()
        }

        updateCargoProject()

        assertThat(f.get(TIMEOUT, TimeUnit.MILLISECONDS)).isNotNull()
    }

    private fun <T> bindToProjectUpdateEvent(callback: (UpdateResult) -> T): Future<T> {
        val f = SettableFuture.create<T>()

        module.messageBus
            .connect()
            .subscribe(
                CargoProjectWorkspaceListener.Topics.UPDATES,
                object: CargoProjectWorkspaceListener {
                    override fun onWorkspaceUpdateCompleted(r: UpdateResult) {
                        assertThat(r is UpdateResult.Ok)

                        f.set(callback(r))
                    }
                })

        return f
    }

    private fun updateCargoProject() {
        module.getComponentOrThrow<CargoProjectWorkspace>().requestUpdateUsing(module.toolchain!!, immediately = true)
    }

    private fun extractReference(path: String): PsiReference {
        val vFile = LocalFileSystem.getInstance().findFileByPath("${myProject.basePath}/$path")!!
        val psiFile = PsiManager.getInstance(myProject).findFile(vFile)!!
        val documentManager = PsiDocumentManager.getInstance(project)

        var referenceOffset = 0
        WriteCommandAction.runWriteCommandAction(project) {
            val document = documentManager.getDocument(psiFile)!!
            val text = document.text
            val refTag = "<ref>"
            referenceOffset = text.indexOf(refTag)
            document.deleteString(referenceOffset, referenceOffset + refTag.length)
            documentManager.commitDocument(document)
        }
        check(referenceOffset > 0)

        return psiFile.findReferenceAt(referenceOffset)!!
    }
}
