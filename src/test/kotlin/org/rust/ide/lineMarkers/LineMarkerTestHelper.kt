/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.lineMarkers

import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.codeInsight.daemon.impl.DaemonCodeAnalyzerImpl
import com.intellij.lang.LanguageCommenters
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import java.awt.event.MouseEvent
import javax.swing.JLabel

class LineMarkerTestHelper(private val fixture: CodeInsightTestFixture) {

    fun doTestByText(fileName: String, source: String) {
        fixture.configureByText(fileName, source)
        doTest()
    }

    fun doTestFromFile(file: VirtualFile) {
        fixture.configureFromExistingVirtualFile(file)
        doTest()
    }

    private fun doTest() {
        fixture.doHighlighting()
        val expected = markersFrom(fixture.editor.document.text)
        val actual = markersFrom(fixture.editor, fixture.project)
        BasePlatformTestCase.assertEquals(expected.joinToString(COMPARE_SEPARATOR), actual.joinToString(COMPARE_SEPARATOR))
    }

    private fun markersFrom(text: String): List<Pair<Int, String>> {
        val commentPrefix = LanguageCommenters.INSTANCE.forLanguage(fixture.file.language).lineCommentPrefix ?: "//"
        val marker = "$commentPrefix - "
        return text.split('\n')
            .withIndex()
            .filter { it.value.contains(marker) }
            .flatMap { row ->
                row.value
                    .substring(row.value.indexOf(marker) + marker.length)
                    .trim()
                    .split(',')
                    .map { Pair(row.index, it) }
            }
            .sortedWith(compareBy({ it.first }, { it.second }))
    }

    private fun markersFrom(editor: Editor, project: Project): List<Pair<Int, String>> =
        DaemonCodeAnalyzerImpl.getLineMarkers(editor.document, project)
            .map {
                Pair(editor.document.getLineNumber(it.element?.textRange?.startOffset as Int),
                    it.lineMarkerTooltip ?: "null")
            }
            .sortedWith(compareBy({ it.first }, { it.second }))

    private companion object {
        private const val COMPARE_SEPARATOR = " | "
    }
}

fun LineMarkerInfo<PsiElement>.invokeNavigationHandler(element: PsiElement?) {
    navigationHandler.navigate(MouseEvent(JLabel(), 0, 0, 0, 0, 0, 0, false), element)
}
