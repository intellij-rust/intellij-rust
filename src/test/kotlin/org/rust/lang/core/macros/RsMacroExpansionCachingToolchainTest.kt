/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.macros

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.impl.LaterInvocator
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.testFramework.builders.ModuleFixtureBuilder
import com.intellij.testFramework.fixtures.impl.TempDirTestFixtureImpl
import com.intellij.util.io.delete
import org.intellij.lang.annotations.Language
import org.rust.TestProject
import org.rust.cargo.RsWithToolchainTestBase
import org.rust.cargo.project.model.cargoProjects
import org.rust.fileTree
import org.rust.fileTreeFromText
import org.rust.lang.core.macros.MacroExpansionManagerImpl.Testmarks.hashBasedRebindCallHit
import org.rust.lang.core.macros.MacroExpansionManagerImpl.Testmarks.hashBasedRebindExactHit
import org.rust.lang.core.macros.MacroExpansionManagerImpl.Testmarks.hashBasedRebindNotHit
import org.rust.lang.core.macros.MacroExpansionManagerImpl.Testmarks.stubBasedRebind
import org.rust.lang.core.psi.RsMacroCall
import org.rust.lang.core.psi.ext.childrenOfType
import org.rust.lang.core.psi.ext.expansion
import org.rust.openapiext.Testmark
import org.rust.openapiext.fullyRefreshDirectory
import org.rust.openapiext.pathAsPath

class RsMacroExpansionCachingToolchainTest : RsWithToolchainTestBase() {
    private val dirFixture = TempDirTestFixtureImpl()
    private var macroExpansionServiceDisposable: Disposable? = null

    override fun setUp() {
        dirFixture.setUp()
        super.setUp()
    }

    override fun tearDown() {
        try {
            val indexableDirectory = project.macroExpansionManager.indexableDirectory
            macroExpansionServiceDisposable?.let { Disposer.dispose(it) }
            indexableDirectory?.parent?.pathAsPath?.delete()
        } finally {
            super.tearDown()
            dirFixture.tearDown()
        }
    }

    override fun tuneFixture(moduleBuilder: ModuleFixtureBuilder<*>) {
        moduleBuilder.addContentRoot(dirFixture.tempDirPath)
    }

    private fun doNothing(): (p: TestProject) -> Unit = {}
    private fun touchFile(path: String): (p: TestProject) -> Unit = { p ->
        val file = p.root.findFileByRelativePath(path)!!
        runWriteAction {
            VfsUtil.saveText(file, VfsUtil.loadText(file) + " ")
        }
    }
    private fun replaceInFile(path: String, find: String, replace: String): (p: TestProject) -> Unit = { p ->
        val file = p.root.findFileByRelativePath(path)!!
        runWriteAction {
            VfsUtil.saveText(file, VfsUtil.loadText(file).replace(find, replace))
        }
    }

    private fun List<RsMacroCall>.collectStamps(): Map<String, Long> =
        associate {
            val expansion = it.expansion ?: error("failed to expand macro ${it.path.referenceName}!")
            it.path.referenceName to expansion.file.virtualFile.timeStamp
        }

    private fun checkReExpanded(action: (p: TestProject) -> Unit, @Language("Rust") code: String, vararg names: String) {
        val p = fileTree {
            toml("Cargo.toml", """
                [package]
                name = "hello"
                version = "0.1.0"
                authors = []
            """)

            dir("src", fileTreeFromText(code))
        }.create(project, dirFixture.getFile(".")!!)

        attachCargoProjectAndExpandMacros(p)
        myFixture.openFileInEditor(p.root.findFileByRelativePath("src/main.rs")!!)
        val oldStamps = myFixture.file.childrenOfType<RsMacroCall>().collectStamps()

        macroExpansionServiceDisposable?.let { Disposer.dispose(it) } // also save
        super.tearDown()
        action(p)
        fullyRefreshDirectory(p.root)
        super.setUp()

        attachCargoProjectAndExpandMacros(p)
        myFixture.openFileInEditor(p.root.findFileByRelativePath("src/main.rs")!!)
        val changed = myFixture.file.childrenOfType<RsMacroCall>().collectStamps().entries
            .filter { oldStamps[it.key] != it.value }
            .map { it.key }
        check(changed == names.toList()) {
            "Expected to re-expand ${names.asList()}, re-expanded $changed instead"
        }
    }

    private fun Testmark.checkReExpanded(
        action: (p: TestProject) -> Unit,
        @Language("Rust") code: String,
        vararg names: String
    ) = this.checkHit { this@RsMacroExpansionCachingToolchainTest.checkReExpanded(action, code, *names) }

    private fun attachCargoProjectAndExpandMacros(p: TestProject) {
        macroExpansionServiceDisposable = project.macroExpansionManager.setUnitTestExpansionModeAndDirectory(MacroExpansionScope.WORKSPACE, "mocked")
        check(project.cargoProjects.attachCargoProject(p.root.findFileByRelativePath("Cargo.toml")!!.pathAsPath))
        val future = project.cargoProjects.refreshAllProjects()
        while (!future.isDone) {
            Thread.sleep(10)
            LaterInvocator.dispatchPendingFlushes()
        }
        project.macroExpansionManager.ensureUpToDate()
    }

    fun `test re-open project without changes`() = stubBasedRebind.checkReExpanded(doNothing(), """
        //- main.rs
        macro_rules! foo { ($ i:ident) => { mod $ i {} } }
        macro_rules! bar { ($ i:ident) => { mod $ i {} } }
        foo!(a);
        bar!(a);
    """)

    fun `test touch definition at separate file`() = stubBasedRebind.checkReExpanded(touchFile("src/foo.rs"), """
        //- foo.rs
        macro_rules! foo { ($ i:ident) => { mod $ i {} } }
        //- bar.rs
        macro_rules! bar { ($ i:ident) => { mod $ i {} } }
        //- main.rs
        #[macro_use]
        mod foo;
        #[macro_use]
        mod bar;
        foo!(a);
        bar!(a);
    """)

    fun `test touch usage at separate file`() = hashBasedRebindExactHit.checkReExpanded(touchFile("src/main.rs"), """
        //- def.rs
        macro_rules! foo { ($ i:ident) => { mod $ i {} } }
        //- main.rs
        #[macro_use]
        mod def;
        foo!(a);
    """)

    fun `test edit usage at same file`() = hashBasedRebindNotHit.checkReExpanded(replaceInFile("src/main.rs", "aaa", "aab"), """
        //- main.rs
        macro_rules! foo { ($ i:ident) => { mod $ i {} } }
        foo!(aaa);
    """, "foo")

    fun `test edit usage at separate file`() = hashBasedRebindNotHit.checkReExpanded(replaceInFile("src/main.rs", "aaa", "aab"), """
        //- def.rs
        macro_rules! foo { ($ i:ident) => { mod $ i {} } }
        //- main.rs
        #[macro_use]
        mod def;
        foo!(aaa);
    """, "foo")

    fun `test edit definition at same file`() = hashBasedRebindCallHit.checkReExpanded(replaceInFile("src/main.rs", "aaa", "aab"), """
        //- main.rs
        macro_rules! foo { ($ i:ident) => { fn $ i() { aaa; } } }
        foo!(a);
    """, "foo")

    fun `test edit definition at separate file`() = stubBasedRebind.checkReExpanded(replaceInFile("src/def.rs", "aaa", "aab"), """
        //- def.rs
        macro_rules! foo { ($ i:ident) => { fn $ i() { aaa; } } }
        //- main.rs
        #[macro_use]
        mod def;
        foo!(a);
    """, "foo")
}
