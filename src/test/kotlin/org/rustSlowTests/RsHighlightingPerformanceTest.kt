/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rustSlowTests

import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiRecursiveElementVisitor
import com.intellij.psi.util.PsiModificationTracker
import com.intellij.util.ui.UIUtil
import org.rust.cargo.RustWithToolchainTestBase
import org.rust.utils.fullyRefreshDirectory
import kotlin.system.measureTimeMillis


class RsHighlightingPerformanceTest : RustWithToolchainTestBase() {
    fun `test highlighting Cargo`() {
        val base = openRealProject("testData/cargo")
        if (base == null) {
            println("SKIP $name: clone Cargo to testData")
            return
        }
        myFixture.configureFromTempProjectFile("src/cargo/core/resolver/mod.rs")

        val modificationCount = currentPsiModificationCount()
        val resolve = measureTimeMillis {
            myFixture.file.accept(object : PsiRecursiveElementVisitor() {
                override fun visitElement(element: PsiElement) {
                    super.visitElement(element)
                    element.reference?.resolve()
                }
            })
        }
        val highlighting = measureTimeMillis {
            myFixture.doHighlighting()
        }

        check(modificationCount == currentPsiModificationCount()) {
            "PSI changed during resolve and highlighting, resolve might be double counted"
        }

        println("resolve = $resolve ms")
        println("highlighting: $highlighting ms")
    }

    private fun currentPsiModificationCount() =
        PsiModificationTracker.SERVICE.getInstance(project).modificationCount

    private fun openRealProject(path: String): VirtualFile? {
        val projectDir = LocalFileSystem.getInstance().findFileByPath(path)
            ?: return null
        runWriteAction {
            VfsUtil.copyDirectory(
                this,
                projectDir,
                cargoProjectDirectory,
                { true }
            )
            fullyRefreshDirectory(cargoProjectDirectory)
        }

        refreshWorkspace()
        UIUtil.dispatchAllInvocationEvents()
        return cargoProjectDirectory
    }
}

