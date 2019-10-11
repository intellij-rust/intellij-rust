/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.builders.ModuleFixtureBuilder
import com.intellij.testFramework.fixtures.CodeInsightFixtureTestCase
import org.rust.*
import org.rust.cargo.project.model.impl.testCargoProjects
import org.rust.cargo.project.settings.rustSettings
import org.rust.cargo.toolchain.RustToolchain

/**
 * This class allows executing real Cargo during the tests.
 *
 * Unlike [org.rust.RsTestBase] it does not use in-memory temporary VFS
 * and instead copies real files.
 */
abstract class RsWithToolchainTestBase : CodeInsightFixtureTestCase<ModuleFixtureBuilder<*>>() {
    open val dataPath: String = ""

    private val toolchain = RustToolchain.suggest()

    protected val cargoProjectDirectory: VirtualFile get() = myFixture.findFileInTempDir(".")

    protected fun FileTree.create(): TestProject =
        create(project, cargoProjectDirectory).apply {
            refreshWorkspace()
        }

    protected fun refreshWorkspace() {
        project.testCargoProjects.discoverAndRefreshSync()
    }

    override fun runTest() {
        val toolchain = toolchain
        if (toolchain == null) {
            System.err.println("SKIP \"$name\": no Rust toolchain found")
            return
        }
        val minRustVersion = findAnnotationInstance<MinRustcVersion>()
        if (minRustVersion != null) {
            val requiredVersion = minRustVersion.semver
            val rustcVersion = toolchain.queryVersions().rustc
            if (rustcVersion == null) {
                System.err.println("SKIP \"$name\": failed to query Rust version")
                return
            }

            if (rustcVersion.semver < requiredVersion) {
                println("SKIP \"$name\": $requiredVersion Rust version required, ${rustcVersion.semver} found")
                return
            }
        }
        super.runTest()
    }

    override fun setUp() {
        super.setUp()

        if (toolchain != null) {
            project.rustSettings.modify { it.toolchain = toolchain }
        }
    }

    override fun tearDown() {
        project.rustSettings.modify { it.toolchain = null }
        super.tearDown()
    }

    protected fun buildProject(builder: FileTreeBuilder.() -> Unit): TestProject =
        fileTree { builder() }.create()

    /** Tries to find the specified annotation on the current test method and then on the current class */
    private inline fun <reified T : Annotation> findAnnotationInstance(): T? =
        javaClass.getMethod(name).getAnnotation(T::class.java) ?: javaClass.getAnnotation(T::class.java)
}
