/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rustSlowTests

import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.util.PsiModificationTracker
import com.intellij.util.ui.UIUtil
import org.rust.cargo.RustWithToolchainTestBase
import org.rust.lang.core.psi.ext.RsReferenceElement
import org.rust.lang.core.psi.ext.descendantsOfType
import org.rust.stdext.Timings
import org.rust.openapiext.fullyRefreshDirectory


class RsHighlightingPerformanceTest : RustWithToolchainTestBase() {
    // It is a performance test, but we don't want to waste time
    // measuring CPU performance
    override fun isPerformanceTest(): Boolean = false

    fun `test highlighting Cargo`() =
        repeatTest { highlightProjectFile("cargo", "https://github.com/rust-lang/cargo", "src/cargo/core/resolver/mod.rs") }

    fun `test highlighting mysql_async`() =
        repeatTest { highlightProjectFile("mysql_async", "https://github.com/blackbeam/mysql_async", "src/conn/mod.rs") }

    private fun repeatTest(f: () -> Timings) {
        var result = Timings()
        println("${name.substring("test ".length)}:")
        repeat(10) {
            result = result.merge(f())
            tearDown()
            setUp()
        }
        result.report()
    }

    private fun highlightProjectFile(name: String, gitUrl: String, filePath: String): Timings {
        val timings = Timings()
        val base = openRealProject("testData/$name")
        if (base == null) {
            println("SKIP $name: git clone $gitUrl testData/$name")
            return timings
        }

        myFixture.configureFromTempProjectFile(filePath)

        val modificationCount = currentPsiModificationCount()

        val refs = timings.measure("collecting") {
            myFixture.file.descendantsOfType<RsReferenceElement>()
        }

        timings.measure("resolve") {
            refs.forEach { it.reference.resolve() }
        }
        timings.measure("highlighting") {
            myFixture.doHighlighting()
        }

        check(modificationCount == currentPsiModificationCount()) {
            "PSI changed during resolve and highlighting, resolve might be double counted"
        }

        return timings
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

