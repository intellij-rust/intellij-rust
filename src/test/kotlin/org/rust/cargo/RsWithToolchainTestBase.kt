/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo

import com.intellij.findAnnotationInstance
import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.RecursionManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.newvfs.impl.VfsRootAccess
import com.intellij.testFramework.builders.ModuleFixtureBuilder
import com.intellij.testFramework.fixtures.CodeInsightFixtureTestCase
import com.intellij.util.ThrowableRunnable
import com.intellij.util.ui.UIUtil
import org.junit.internal.runners.JUnit38ClassRunner
import org.junit.runner.RunWith
import org.rust.*
import org.rust.cargo.project.model.impl.testCargoProjects
import org.rust.cargo.toolchain.tools.rustc
import org.rust.ide.experiments.RsExperiments
import org.rust.lang.core.macros.macroExpansionManager
import org.rust.openapiext.pathAsPath
import org.rust.stdext.RsResult

/**
 * This class allows executing real Cargo during the tests.
 *
 * Unlike [org.rust.RsTestBase] it does not use in-memory temporary VFS
 * and instead copies real files.
 */
@RunWith(JUnit38ClassRunner::class) // TODO: drop the annotation when issue with Gradle test scanning go away
abstract class RsWithToolchainTestBase : CodeInsightFixtureTestCase<ModuleFixtureBuilder<*>>() {

    protected lateinit var rustupFixture: RustupTestFixture

    open val dataPath: String = ""

    open val disableMissedCacheAssertions: Boolean get() = true
    protected open val fetchActualStdlibMetadata: Boolean get() = false

    protected val cargoProjectDirectory: VirtualFile get() = myFixture.findFileInTempDir(".")

    private val earlyTestRootDisposable = Disposer.newDisposable()

    protected fun FileTree.create(): TestProject =
        create(project, cargoProjectDirectory).apply {
            rustupFixture.toolchain
                ?.rustc()
                ?.getStdlibPathFromSysroot(cargoProjectDirectory.pathAsPath)
                ?.let { VfsRootAccess.allowRootAccess(testRootDisposable, it) }

            refreshWorkspace()
        }

    protected fun refreshWorkspace() {
        project.testCargoProjects.discoverAndRefreshSync()
    }

    override fun runTestRunnable(testRunnable: ThrowableRunnable<Throwable>) {
        val skipReason = rustupFixture.skipTestReason
        if (skipReason != null) {
            System.err.println("SKIP \"$name\": $skipReason")
            return
        }

        val reason = checkRustcVersionRequirements {
            val rustcVersion = rustupFixture.toolchain!!.rustc().queryVersion()?.semver
            if (rustcVersion != null) RsResult.Ok(rustcVersion) else RsResult.Err("\"$name\": failed to query Rust version")
        }
        if (reason != null) {
            System.err.println("SKIP $reason")
            return
        }

        super.runTestRunnable(testRunnable)
    }

    override fun setUp() {
        super.setUp()
        rustupFixture = createRustupFixture()
        rustupFixture.setUp()
        if (disableMissedCacheAssertions) {
            RecursionManager.disableMissedCacheAssertions(testRootDisposable)
        }
        setupExperimentalFeatures()
        setupResolveEngine(project, testRootDisposable)
        findAnnotationInstance<ExpandMacros>()?.let { ann ->
            Disposer.register(
                earlyTestRootDisposable,
                project.macroExpansionManager.setUnitTestExpansionModeAndDirectory(
                    ann.mode,
                    ann.cache.takeIf { it.isNotEmpty() } ?: name
                )
            )
        }
        // RsExperiments.FETCH_ACTUAL_STDLIB_METADATA significantly slows down tests.
        // It's possible to make it cheap, we just need to wrap `Rustup.fetchStdlib()` call into
        // `UnitTestRustcCache.cached()`
        setExperimentalFeatureEnabled(RsExperiments.FETCH_ACTUAL_STDLIB_METADATA, fetchActualStdlibMetadata, testRootDisposable)
    }

    private fun setupExperimentalFeatures() {
        for (feature in findAnnotationInstance<WithExperimentalFeatures>()?.features.orEmpty()) {
            setExperimentalFeatureEnabled(feature, true, testRootDisposable)
        }
    }

    override fun tearDown() {
        Disposer.dispose(earlyTestRootDisposable)
        rustupFixture.tearDown()
        super.tearDown()
        checkMacroExpansionFileSystemAfterTest()
    }

    protected open fun createRustupFixture(): RustupTestFixture = RustupTestFixture(project)

    override fun getTestRootDisposable(): Disposable {
        return if (myFixture != null) myFixture.testRootDisposable else super.getTestRootDisposable()
    }

    protected fun buildProject(builder: FileTreeBuilder.() -> Unit): TestProject =
        fileTree { builder() }.create()

    /**
     * Tries to launches [action]. If it returns `false`, invokes [UIUtil.dispatchAllInvocationEvents] and tries again
     *
     * Can be used to wait file system refresh, for example
     */
    protected fun runWithInvocationEventsDispatching(
        errorMessage: String = "Failed to invoke `action` successfully",
        retries: Int = 1000,
        action: () -> Boolean
    ) {
        repeat(retries) {
            UIUtil.dispatchAllInvocationEvents()
            if (action()) {
                return
            }
            Thread.sleep(10)
        }
        error(errorMessage)
    }
}
