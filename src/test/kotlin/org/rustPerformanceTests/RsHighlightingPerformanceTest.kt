/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rustPerformanceTests

import com.intellij.openapi.util.Disposer
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.util.PsiModificationTracker
import org.rust.lang.core.macros.MacroExpansionScope
import org.rust.lang.core.macros.macroExpansionManager
import org.rust.lang.core.psi.RsFunction
import org.rust.lang.core.psi.ext.RsReferenceElement
import org.rust.lang.core.psi.ext.descendantsOfType
import org.rust.stdext.Timings


class RsHighlightingPerformanceTest : RsRealProjectTestBase() {
    // It is a performance test, but we don't want to waste time
    // measuring CPU performance
    override fun isPerformanceTest(): Boolean = false

    fun `test highlighting Cargo`() =
        repeatTest { highlightProjectFile(CARGO, "src/cargo/core/resolver/mod.rs") }

    fun `test highlighting mysql_async`() =
        repeatTest { highlightProjectFile(MYSQL_ASYNC, "src/conn/mod.rs") }

    fun `test highlighting mysql_async 2`() =
        repeatTest { highlightProjectFile(MYSQL_ASYNC, "src/connection_like/mod.rs") }

    private fun repeatTest(f: () -> Timings) {
        var result = Timings()
        println("${name.substring("test ".length)}:")
        repeat(10) {
            val disposable = project.macroExpansionManager.setUnitTestExpansionModeAndDirectory(MacroExpansionScope.ALL, name)
            result = result.merge(f())
            Disposer.dispose(disposable)
            tearDown()
            setUp()
        }
        result.report()
    }

    private fun highlightProjectFile(info: RealProjectInfo, filePath: String): Timings {
        val timings = Timings()
        val base = openRealProject(info) ?: return timings

        myFixture.configureFromTempProjectFile(filePath)

        timings.measure("macros") {
            project.macroExpansionManager.ensureUpToDate()
        }

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

        timings.measure("resolve_cached") {
            refs.forEach { it.reference.resolve() }
        }

        myFixture.file.descendantsOfType<RsFunction>()
            .asSequence()
            .mapNotNull { it.block?.stmtList?.lastOrNull() }
            .forEach { stmt ->
                myFixture.editor.caretModel.moveToOffset(stmt.textOffset)
                myFixture.type("2+2;")
                PsiDocumentManager.getInstance(project).commitAllDocuments() // process PSI modification events

                timings.measureAverage("resolve_after_typing") {
                    refs.forEach { it.reference.resolve() }
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

    private fun currentPsiModificationCount() =
        PsiModificationTracker.SERVICE.getInstance(project).modificationCount
}

