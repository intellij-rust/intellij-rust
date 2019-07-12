/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rustPerformanceTests

import com.intellij.codeInspection.ex.InspectionToolRegistrar
import com.intellij.openapi.util.Disposer
import org.junit.ComparisonFailure
import org.rust.ide.inspections.RsLocalInspectionTool
import org.rust.lang.RsFileType
import org.rust.lang.core.macros.MacroExpansionScope
import org.rust.lang.core.macros.macroExpansionManager
import org.rust.lang.core.psi.RsFile
import org.rust.lang.core.psi.ext.cargoWorkspace
import org.rust.openapiext.toPsiFile
import java.lang.reflect.Field

class RsRealProjectAnalysisTest : RsRealProjectTestBase() {

    /** Don't run it on Rustc! It's a kind of stress-test */
    fun `test analyze rustc`() = doTest(RUSTC)

    fun `test analyze Cargo`() = doTest(CARGO)
    fun `test analyze mysql_async`() = doTest(MYSQL_ASYNC)
    fun `test analyze tokio`() = doTest(TOKIO)
    fun `test analyze amethyst`() = doTest(AMETHYST)
    fun `test analyze clap`() = doTest(CLAP)
    fun `test analyze diesel`() = doTest(DIESEL)
    fun `test analyze rust_analyzer`() = doTest(RUST_ANALYZER)
    fun `test analyze xi_editor`() = doTest(XI_EDITOR)
    fun `test analyze juniper`() = doTest(JUNIPER)

    private fun doTest(info: RealProjectInfo, failOnFirstFileWithErrors: Boolean = false) {
        Disposer.register(
            testRootDisposable,
            project.macroExpansionManager.setUnitTestExpansionModeAndDirectory(MacroExpansionScope.ALL, name)
        )
        val inspections = InspectionToolRegistrar.getInstance().createTools()
            .map { it.tool }
            .filterIsInstance<RsLocalInspectionTool>()
        myFixture.enableInspections(*inspections.toTypedArray())

        println("Opening the project")
        val base = openRealProject(info) ?: return

        println("Collecting files to analyze")
        val filesToCheck = base.findDescendants {
            it.fileType == RsFileType && run {
                val file = it.toPsiFile(project)
                file is RsFile && file.crateRoot != null && file.cargoWorkspace != null
            }
        }

        if (failOnFirstFileWithErrors) {
            println("Analyzing...")
            myFixture.testHighlightingAllFiles(
                /* checkWarnings = */ false,
                /* checkInfos = */ false,
                /* checkWeakWarnings = */ false,
                *filesToCheck.toTypedArray()
            )
        } else {
            val exceptions = filesToCheck.mapNotNull { file ->
                val path = file.path.substring(base.path.length + 1)
                println("Analyzing $path")
                try {
                    myFixture.testHighlighting(
                        /* checkWarnings = */ false,
                        /* checkInfos = */ false,
                        /* checkWeakWarnings = */ false,
                        file
                    )
                    null
                } catch (e: ComparisonFailure) {
                    e to path
                }
            }

            if (exceptions.isNotEmpty()) {
                error("Error annotations found:\n\n" + exceptions.joinToString("\n\n") { (e, path) ->
                    "$path:\n${e.detailMessage}"
                })
            }
        }
    }
}

private val THROWABLE_DETAILED_MESSAGE_FIELD: Field = run {
    val field = Throwable::class.java.getDeclaredField("detailMessage")
    field.isAccessible = true
    field
}

/**
 * Retrieves original value of detailMessage field of [Throwable] class.
 * It is needed because [ComparisonFailure] overrides [Throwable.message]
 * method so we can't get the original value without reflection
 */
private val Throwable.detailMessage: CharSequence
    get() = THROWABLE_DETAILED_MESSAGE_FIELD.get(this) as CharSequence
