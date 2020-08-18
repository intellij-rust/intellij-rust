/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rustPerformanceTests

import com.intellij.codeInspection.ex.InspectionToolRegistrar
import com.intellij.ide.annotator.AnnotatorBase
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.util.Disposer
import org.rust.ide.annotator.RsErrorAnnotator
import org.rust.ide.inspections.RsLocalInspectionTool
import org.rust.lang.RsFileType
import org.rust.lang.core.macros.MacroExpansionScope
import org.rust.lang.core.macros.macroExpansionManager
import org.rust.lang.core.psi.RsFile
import org.rust.openapiext.toPsiFile

open class RsRealProjectAnalysisTest : RsRealProjectTestBase() {

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

    private val earlyTestRootDisposable = Disposer.newDisposable()

    protected fun doTest(info: RealProjectInfo, failOnFirstFileWithErrors: Boolean = false) {
        val errorConsumer = if (failOnFirstFileWithErrors) FAIL_FAST else COLLECT_ALL_EXCEPTIONS
        doTest(info, errorConsumer)
    }

    protected fun doTest(info: RealProjectInfo, consumer: AnnotationConsumer) {
        Disposer.register(
            earlyTestRootDisposable,
            project.macroExpansionManager.setUnitTestExpansionModeAndDirectory(MacroExpansionScope.ALL, name)
        )
        AnnotatorBase.enableAnnotator(RsErrorAnnotator::class.java, testRootDisposable)
        val inspections = InspectionToolRegistrar.getInstance().createTools()
            .map { it.tool }
            .filterIsInstance<RsLocalInspectionTool>()
        myFixture.enableInspections(*inspections.toTypedArray())

        println("Opening the project `${info.name}`")
        val base = openRealProject(info) ?: return

        println("Collecting files to analyze")
        val filesToCheck = base.findDescendants {
            it.fileType == RsFileType && run {
                val file = it.toPsiFile(project)
                file is RsFile && file.crateRoot != null && file.cargoWorkspace != null
            }
        }
        for (file in filesToCheck) {
            val path = file.path.substring(base.path.length + 1)
            println("Analyzing $path")
            myFixture.openFileInEditor(file)
            val infos = myFixture.doHighlighting(HighlightSeverity.ERROR)
            val text = myFixture.editor.document.text
            for (highlightInfo in infos) {
                val position = myFixture.editor.offsetToLogicalPosition(highlightInfo.startOffset)
                val annotation = Annotation(
                    path,
                    position.line,
                    position.column,
                    text.substring(highlightInfo.startOffset, highlightInfo.endOffset),
                    highlightInfo.description,
                    highlightInfo.inspectionToolId
                )
                consumer.consumeAnnotation(annotation)
            }
        }
        consumer.finish()
    }

    override fun tearDown() {
        Disposer.dispose(earlyTestRootDisposable)
        super.tearDown()
    }

    companion object {

        val FAIL_FAST = object : AnnotationConsumer {
            override fun consumeAnnotation(annotation: Annotation) {
                error(annotation.toString())
            }
            override fun finish() {}
        }

        val COLLECT_ALL_EXCEPTIONS = object : AnnotationConsumer {

            val annotations = mutableListOf<Annotation>()

            override fun consumeAnnotation(annotation: Annotation) {
                annotations += annotation
            }

            override fun finish() {
                if (annotations.isNotEmpty()) {
                    error("Error annotations found:\n\n" + annotations.joinToString("\n\n"))
                }
            }
        }
    }

    interface AnnotationConsumer {
        fun consumeAnnotation(annotation: Annotation)
        fun finish()
    }

    data class Annotation(
        val filePath: String,
        val line: Int,
        val column: Int,
        val highlightedText: String,
        val error: String,
        val inspectionToolId: String?
    ) {
        override fun toString(): String {
            val suffix = if (inspectionToolId != null) " by $inspectionToolId" else ""
            return "$filePath:$line:$column '$highlightedText' ($error)$suffix"
        }
    }
}
