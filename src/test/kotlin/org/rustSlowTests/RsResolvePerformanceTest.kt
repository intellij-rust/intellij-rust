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
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiRecursiveElementVisitor
import org.rust.cargo.RustWithToolchainTestBase
import org.rust.lang.core.psi.RsFile
import org.rust.utils.fullyRefreshDirectory
import kotlin.system.measureTimeMillis

class RsResolvePerformanceTest : RustWithToolchainTestBase() {
    fun `test resolving Cargo`() {
        val base = openRealProject("testData/cargo")
        if (base == null) {
            println("SKIP $name: clone Cargo to testData")
            return
        }
        val toml = base.findFileByRelativePath("src/cargo/util/toml.rs")
            ?: error("failed to find toml file")
        val psiManager = PsiManager.getInstance(project)
        val psi = psiManager.findFile(toml) as RsFile

        val elapsed = measureTimeMillis {
            psi.accept(object : PsiRecursiveElementVisitor() {
                override fun visitElement(element: PsiElement) {
                    super.visitElement(element)
                    element.reference?.resolve()
                }
            })
        }

        println("$name: $elapsed ms")
    }

    @Suppress("unused")
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
        return cargoProjectDirectory
    }
}

