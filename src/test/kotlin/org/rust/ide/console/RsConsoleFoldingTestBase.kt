/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.console

import com.intellij.execution.impl.ConsoleViewImpl
import com.intellij.execution.process.NopProcessHandler
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.ui.ConsoleViewContentType
import com.intellij.openapi.util.Disposer
import org.rust.RsTestBase
import org.rust.fileTreeFromText

abstract class RsConsoleFoldingTestBase : RsTestBase() {
    private lateinit var console: ConsoleViewImpl

    public override fun setUp() {
        super.setUp()

        console = createConsole()
        console.setUpdateFoldingsEnabled(true)
    }

    public override fun tearDown() {
        try {
            Disposer.dispose(console)
        } catch (e: Throwable) {
            addSuppressedException(e)
        } finally {
            super.tearDown()
        }
    }

    protected fun doFoldingTest(tree: String, text: String) {
        fileTreeFromText(tree).create()

        val (input, expectedFolds) = createConsoleFoldInput(text)

        val console = console
        console.print(input, ConsoleViewContentType.NORMAL_OUTPUT)
        console.flushDeferredText()

        val editor = console.editor
        val regions = editor.foldingModel.allFoldRegions

        val expectedText = renderTextWithFolds(input, expectedFolds)
        val actualText = renderTextWithFolds(input,
            regions.map { Fold(it.placeholderText, it.startOffset, it.endOffset) })
        assertEquals(expectedText, actualText)
    }

    private fun createConsole(): ConsoleViewImpl {
        val console = RsConsoleView(project)
        console.component // initialize component
        val processHandler: ProcessHandler = NopProcessHandler()
        processHandler.startNotify()
        console.attachToProcess(processHandler)
        return console
    }

    companion object {
        private fun renderTextWithFolds(text: String, folds: List<Fold>): String = buildString {
            var offset = 0
            folds.forEach { fold ->
                append(text.substring(offset until fold.startOffset))
                append(fold.text)
                offset = fold.endOffset
            }
            append(text.substring(offset))
        }

        private fun createConsoleFoldInput(text: String): Pair<String, List<Fold>> {
            val expectedFolds = mutableListOf<Fold>()
            val input = buildString {
                var offset = 0
                var currentText: String? = null
                var currentStartOffset: Int? = null
                for (line in text.trimIndent().lineSequence()) {
                    when {
                        line.startsWith("//foldstart") -> {
                            currentText = line.removePrefix("//foldstart ").trim()
                            currentStartOffset = offset
                        }
                        line.startsWith("//foldend") -> {
                            check(currentText != null)
                            check(currentStartOffset != null)
                            expectedFolds.add(Fold(currentText, currentStartOffset, offset - 1))
                            currentText = null
                            currentStartOffset = null
                        }
                        else -> {
                            append(line)
                            append('\n')
                            offset += line.length + 1
                        }
                    }
                }
                assertNull(currentText)
                assertNull(currentStartOffset)
            }
            return Pair(input, expectedFolds)
        }

        private data class Fold(val text: String, val startOffset: Int, val endOffset: Int)
    }
}
