/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rustPerformanceTests

import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.RecursionManager
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.util.PsiModificationTracker
import org.rust.lang.core.macros.MacroExpansionScope
import org.rust.lang.core.macros.macroExpansionManager
import org.rust.lang.core.psi.RsFunction
import org.rust.lang.core.psi.RsPsiFactory
import org.rust.lang.core.psi.ext.RsReferenceElement
import org.rust.lang.core.psi.ext.block
import org.rust.lang.core.psi.ext.descendantsOfType
import org.rust.lang.core.psi.rustPsiManager
import org.rust.openapiext.runWriteCommandAction
import org.rust.stdext.Timings
import org.rust.stdext.repeatBenchmark


class RsHighlightingPerfTest : RsRealProjectTestBase() {

    fun `test highlighting Cargo`() =
        repeatTest { highlightProjectFile(CARGO, "src/cargo/core/resolver/mod.rs", it) }

    fun `test highlighting mysql_async`() =
        repeatTest { highlightProjectFile(MYSQL_ASYNC, "src/conn/mod.rs", it) }

    fun `test highlighting mysql_async 2`() =
        repeatTest { highlightProjectFile(MYSQL_ASYNC, "src/connection_like/mod.rs", it) }

    private fun repeatTest(f: (Timings) -> Unit) {
        println("${name.substring("test ".length)}:")
        repeatBenchmark {
            val disposable = project.macroExpansionManager.setUnitTestExpansionModeAndDirectory(MacroExpansionScope.ALL, name)
            f(it)
            Disposer.dispose(disposable)
            super.tearDown()
            super.setUp()
        }
    }

    private fun highlightProjectFile(info: RealProjectInfo, filePath: String, timings: Timings): Timings {
        openRealProject(info) ?: return timings

        myFixture.configureFromTempProjectFile(filePath)

        val modificationCount = currentPsiModificationCount()

        val refs = timings.measure("collecting") {
            myFixture.file.descendantsOfType<RsReferenceElement>()
        }

        timings.measure("resolve") {
            refs.forEach { it.reference?.resolve() }
        }
        timings.measure("highlighting") {
            myFixture.doHighlighting()
        }

        check(modificationCount == currentPsiModificationCount()) {
            "PSI changed during resolve and highlighting, resolve might be double counted"
        }

        timings.measure("resolve_cached") {
            refs.forEach { it.reference?.resolve() }
        }

        val added = project.runWriteCommandAction {
            myFixture.file.add(RsPsiFactory(project).createFunction("fn foo() {}"))
        }
        project.runWriteCommandAction {
            added.delete()
        }

        timings.measure("resolve_after_workspace_modification") {
            refs.forEach { it.reference?.resolve() }
        }

        runWriteAction {
            project.rustPsiManager.incRustStructureModificationCount()
        }

        timings.measure("resolve_after_deps_modification") {
            refs.forEach { it.reference?.resolve() }
        }

        myFixture.file.descendantsOfType<RsFunction>()
            .asSequence()
            .mapNotNull { it.block?.stmtList?.lastOrNull() }
            .forEach { stmt ->
                myFixture.editor.caretModel.moveToOffset(stmt.textOffset)
                myFixture.type("2+2;")
                PsiDocumentManager.getInstance(project).commitAllDocuments() // process PSI modification events

                timings.measureAverage("resolve_after_typing") {
                    refs.forEach { it.reference?.resolve() }
                }
            }

        myFixture.file.descendantsOfType<RsFunction>()
            .asSequence()
            .mapNotNull { it.block?.stmtList?.lastOrNull() }
            .forEach { stmt ->
                myFixture.editor.caretModel.moveToOffset(stmt.textOffset)
                // replace to `myFixture.type("Hash;")` to make it 10x slower
                myFixture.type("HashMa;")
                myFixture.editor.caretModel.moveCaretRelatively(-1, 0, false, false, false)
                timings.measureAverage("completion") {
                    myFixture.completeBasic()
                }
            }

        return timings
    }


    // BACKCOMPAT: 2022.1
    @Suppress("DEPRECATION", "UnstableApiUsage")
    private fun currentPsiModificationCount() =
        PsiModificationTracker.SERVICE.getInstance(project).modificationCount

    override val disableMissedCacheAssertions: Boolean get() = false
    private val lastDisposable = Disposer.newDisposable("RsHighlightingPerfTest last")

    override fun setUp() {
        super.setUp()
        RecursionManager.disableMissedCacheAssertions(lastDisposable)
    }

    override fun tearDown() {
        super.tearDown()
        Disposer.dispose(lastDisposable)
    }
}

