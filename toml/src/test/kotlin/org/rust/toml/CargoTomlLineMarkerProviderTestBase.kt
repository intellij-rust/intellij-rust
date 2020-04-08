/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.toml

import com.intellij.codeInsight.daemon.impl.DaemonCodeAnalyzerImpl
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import org.intellij.lang.annotations.Language
import org.rust.RsTestBase

abstract class CargoTomlLineMarkerProviderTestBase : RsTestBase() {
    protected fun doTestByText(@Language("Toml") source: String) {
        myFixture.configureByText("Cargo.toml", source)
        myFixture.doHighlighting()
        val expected = markersFrom(source)
        val actual = markersFrom(myFixture.editor, myFixture.project)
        assertEquals(expected.joinToString(COMPARE_SEPARATOR), actual.joinToString(COMPARE_SEPARATOR))
    }

    private fun markersFrom(text: String): List<Pair<Int, String>> =
        text.lines()
            .filter { it.contains(MARKER) }
            .mapIndexed { index, line ->
                index to line.substringAfter(MARKER).trim()
            }

    private fun markersFrom(editor: Editor, project: Project): List<Pair<Int, String>> =
        DaemonCodeAnalyzerImpl.getLineMarkers(editor.document, project)
            .map {
                val startOffset = it.element!!.textRange.startOffset
                editor.document.getLineNumber(startOffset) to it.lineMarkerTooltip!!
            }
            .sortedBy { it.first }

    private companion object {
        const val MARKER = "# - "
        const val COMPARE_SEPARATOR = " | "
    }
}
