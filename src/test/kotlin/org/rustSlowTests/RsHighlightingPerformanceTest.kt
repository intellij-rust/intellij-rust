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
    // It is a performance test, but we don't want to waste time
    // measuring CPU performance
    override fun isPerformanceTest(): Boolean = false

    fun `test highlighting Cargo`() =
        highlightProjectFile("cargo", "https://github.com/rust-lang/cargo", "src/cargo/core/resolver/mod.rs")

    fun `test highlighting mysql_async`() =
        highlightProjectFile("mysql_async", "https://github.com/blackbeam/mysql_async", "src/conn/mod.rs")

    private fun highlightProjectFile(name: String, gitUrl: String, filePath: String) {
        val base = openRealProject("testData/$name")
        if (base == null) {
            println("SKIP $name: git clone $gitUrl testData/$name")
            return
        }

        myFixture.configureFromTempProjectFile(filePath)

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
        val projectDir = LocalFileSystem.getInstance().refreshAndFindFileByPath(path)
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

