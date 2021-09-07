/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.toolchain

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import org.rust.FileTreeBuilder
import org.rust.cargo.RsWithToolchainTestBase
import org.rust.cargo.project.settings.toolchain
import org.rust.cargo.toolchain.tools.Cargo
import org.rust.cargo.toolchain.tools.cargo
import org.rust.fileTree
import org.rust.ide.newProject.RsCustomTemplate
import org.rust.ide.newProject.RsGenericTemplate
import org.rust.ide.newProject.RsProjectTemplate
import org.rust.ide.newProject.makeProject
import org.rust.openapiext.modules

class CargoProjectGenerationTest : RsWithToolchainTestBase() {
    private val cargo: Cargo
        get() = project.toolchain?.cargo()!!

    fun `test binary project generation`() = checkTemplate(RsGenericTemplate.CargoBinaryTemplate) {
        dir("src") {
            file("main.rs")
        }
        file("Cargo.toml")
    }

    fun `test library project generation`() = checkTemplate(RsGenericTemplate.CargoLibraryTemplate) {
        dir("src") {
            file("lib.rs")
        }
        file("Cargo.toml")
    }

    fun `test proc macro project generation`() = checkTemplate(RsCustomTemplate.ProcMacroTemplate) {
        dir("src") {
            file("lib.rs")
        }
        file("Cargo.toml")
    }

    fun `test wasm-pack project generation`() = checkTemplate(RsCustomTemplate.WasmPackTemplate) {
        dir("src") {
            file("lib.rs")
            file("utils.rs")
        }
        file("Cargo.toml")
    }

    fun `test cargo init with git`() {
        cargo.init(project, testRootDisposable, myFixture.baseDir, "foo", false, "git")

        for (path in listOf(".gitignore", ".git")) {
            check(myFixture.baseDir.findFileByRelativePath(path) != null) {
                "Generated project should contain $path"
            }
        }
    }

    fun `test cargo init without git`() {
        cargo.init(project, testRootDisposable, myFixture.baseDir, "foo", false, "none")

        for (path in listOf(".gitignore", ".git")) {
            check(myFixture.baseDir.findFileByRelativePath(path) == null) {
                "Generated project shouldn't contain $path"
            }
        }
    }

    private fun checkTemplate(template: RsProjectTemplate, builder: FileTreeBuilder.() -> Unit) {
        val fileTree = fileTree(builder)

        if (template is RsCustomTemplate && cargo.checkNeedInstallCargoGenerate()) {
            System.err.println("SKIP \"$name\": cargo-generate is not installed")
            return
        }

        cargo.makeProject(
            project,
            project.modules.first(),
            myFixture.baseDir,
            "foo",
            template
        )

        fileTree.assertContains(myFixture.baseDir)
    }

    private val CodeInsightTestFixture.baseDir: VirtualFile
        get() = findFileInTempDir("")!!
}
