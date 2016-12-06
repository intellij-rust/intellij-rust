package org.rust.cargo.toolchain

import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiReference
import com.intellij.psi.stubs.StubIndex
import com.intellij.util.concurrency.Semaphore
import com.intellij.util.ui.UIUtil
import org.rust.cargo.RustWithToolchainTestBase
import org.rust.cargo.project.settings.toolchain
import org.rust.cargo.project.workspace.CargoProjectWorkspace
import org.rust.cargo.project.workspace.CargoProjectWorkspace.UpdateResult
import org.rust.cargo.util.cargoProject
import org.rust.cargo.util.getComponentOrThrow
import org.rust.lang.core.stubs.index.RustModulesIndex

class CargoProjectResolveTest : RustWithToolchainTestBase() {

    override val dataPath: String = "src/test/resources/org/rust/cargo/toolchain/fixtures"

    override fun runTest() {
        // IDEA 15 fails to execute `onComplete` callback in test mode
        // if not on the EDT. I don't see a clean workaround, so let's just
        // skip these tests then :(
        if (ApplicationInfo.getInstance().majorVersion == "15") {
            return
        }
        super.runTest()
    }

    fun testResolveExternalLibrary() = resolveRefInFile("external_library", "src/main.rs")
    fun testResolveLocalPackage() = resolveRefInFile("local_package", "src/main.rs")
    fun testResolveLocalPackageMod() = resolveRefInFile("local_package_mod", "src/bar.rs")
    fun testModuleRelations() = resolveRefInFile("mods", "src/foo.rs")
    fun testKebabCase() = resolveRefInFile("kebab-case", "src/main.rs")

    // Test that we don't choke on winapi crate, which uses **A LOT** of
    // glob imports and is just **ENORMOUS**
    fun testWinTorture() = resolveRefInFile("win_torture", "src/main.rs", unresolved = true)

    private fun resolveRefInFile(project: String, fileWithRef: String, unresolved: Boolean = false) =
        withProject(project) {
            // make sure that indexes do not depend on cargo project
            populateIndexes()

            val semaphore = Semaphore()
            semaphore.down()
            updateCargoProject(semaphore)

            while (!semaphore.waitFor(100)) {
                UIUtil.dispatchAllInvocationEvents()
            }

            if (module.cargoProject == null) {
                error("Failed to update a test Cargo project")
            }

            val result = extractReference(fileWithRef).resolve()
            if (unresolved) {
                check(result == null) { "Reference is erroneously resolved" }
            } else {
                checkNotNull(result) {
                    "Unresolved reference in $fileWithRef"
                }
            }
        }

    private fun populateIndexes() = StubIndex.getInstance().getAllKeys(RustModulesIndex.KEY, myProject)

    private fun updateCargoProject(semaphore: Semaphore) {
        val workspace = module.getComponentOrThrow<CargoProjectWorkspace>()
        workspace.requestImmediateUpdate(project.toolchain!!) { result ->
            when (result) {
                is UpdateResult.Err -> error("Failed to update a project during tests: ${result.error.message}")
            }
            semaphore.up()
        }
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
