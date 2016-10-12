package org.rust.cargo.toolchain

import com.google.common.util.concurrent.SettableFuture
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiReference
import com.intellij.psi.stubs.StubIndex
import org.assertj.core.api.Assertions.assertThat
import org.rust.cargo.RustWithToolchainTestBase
import org.rust.cargo.project.settings.toolchain
import org.rust.cargo.project.workspace.CargoProjectWorkspace
import org.rust.cargo.project.workspace.CargoProjectWorkspaceListener
import org.rust.cargo.project.workspace.CargoProjectWorkspaceListener.UpdateResult
import org.rust.cargo.util.getComponentOrThrow
import org.rust.lang.core.stubs.index.RustModulesIndex
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit

class CargoProjectResolveTest : RustWithToolchainTestBase() {

    override val dataPath: String = "src/test/resources/org/rust/cargo/toolchain/fixtures"

    private val TIMEOUT: Long = 10 * 1000 /* millis */

    fun testResolveExternalLibrary() = resolveRefInFile("external_library", "src/main.rs")
    fun testResolveLocalPackage() = resolveRefInFile("local_package", "src/main.rs")
    fun testResolveLocalPackageMod() = resolveRefInFile("local_package_mod", "src/bar.rs")
    fun testModuleRelations() = resolveRefInFile("mods", "src/foo.rs")
    fun testKebabCase() = resolveRefInFile("kebab-case", "src/main.rs")

    // Test that we can resolve winapi crate, which uses **A LOT** of
    // glob imports and is just **ENORMOUS**
    fun testWinTorture() = resolveRefInFile("win_torture", "src/main.rs")

    private fun resolveRefInFile(project: String, fileWithRef: String) = withProject(project) {
        val f = bindToProjectUpdateEvent {
            val reference = extractReference(fileWithRef)
            reference.resolve()
        }

        // make sure that indexes do not depend on cargo project
        populateIndexes()

        updateCargoProject()

        assertThat(f.get(TIMEOUT, TimeUnit.MILLISECONDS)).isNotNull()
    }

    private fun populateIndexes() {
        StubIndex.getInstance().getAllKeys(RustModulesIndex.KEY, myProject)
    }

    private fun <T> bindToProjectUpdateEvent(callback: (UpdateResult) -> T): Future<T> {
        val f = SettableFuture.create<T>()

        module.messageBus
            .connect()
            .subscribe(
                CargoProjectWorkspaceListener.Topics.UPDATES,
                object : CargoProjectWorkspaceListener {
                    override fun onWorkspaceUpdateCompleted(r: UpdateResult) {
                        assertThat(r is UpdateResult.Ok)
                        f.set(callback(r))
                    }
                })

        return f
    }

    private fun updateCargoProject() {
        module.getComponentOrThrow<CargoProjectWorkspace>().requestUpdateUsing(project.toolchain!!, immediately = true)
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
