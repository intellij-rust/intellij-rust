package org.rust.ide.annotator

import com.intellij.codeInsight.daemon.impl.DaemonCodeAnalyzerImpl
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import org.rust.lang.RustFileType
import org.rust.lang.RustTestCaseBase

abstract class RustLineMarkerProviderTestBase : RustTestCaseBase() {
    override val dataPath = ""

    protected fun doTestByText(source: String) {
        myFixture.configureByText(RustFileType, source)
        myFixture.doHighlighting()
        val expected = markersFrom(source)
        val actual = markersFrom(myFixture.editor, myFixture.project)
        assertEquals(expected.joinToString(COMPARE_SEPARATOR), actual.joinToString(COMPARE_SEPARATOR))
    }

    private fun markersFrom(text: String) =
        text.split('\n')
            .withIndex()
            .filter { it.value.contains(MARKER) }
            .map { Pair(it.index, it.value.substring(it.value.indexOf(MARKER) + MARKER.length).trim()) }
            .toList()

    private fun markersFrom(editor: Editor, project: Project) =
        DaemonCodeAnalyzerImpl.getLineMarkers(editor.document, project)
            .map {
                Pair(editor.document.getLineNumber(it.element?.textRange?.startOffset as Int),
                    it.lineMarkerTooltip)
            }
            .sortedBy { it.first }
            .toList()

    private companion object {
        val MARKER = "// - "
        val COMPARE_SEPARATOR = " | "
    }
}
