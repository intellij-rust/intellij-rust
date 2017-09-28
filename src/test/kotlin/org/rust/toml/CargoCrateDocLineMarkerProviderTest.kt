/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.toml

import com.intellij.codeInsight.daemon.impl.DaemonCodeAnalyzerImpl
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.testFramework.LightProjectDescriptor
import org.intellij.lang.annotations.Language
import org.rust.lang.RsTestBase

class CargoCrateDocLineMarkerProviderTest : RsTestBase() {
    protected fun doTestByText(@Language("toml") source: String) {
        myFixture.configureByText("cargo.toml", source)
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

    private fun markersFrom(editor: Editor, project: Project) =
        DaemonCodeAnalyzerImpl.getLineMarkers(editor.document, project)
            .map {
                Pair(editor.document.getLineNumber(it.element?.textRange?.startOffset as Int), it.lineMarkerTooltip)
            }
            .sortedBy { it.first }

    private companion object {
        val MARKER = "# - "
        val COMPARE_SEPARATOR = " | "
    }

    override fun getProjectDescriptor(): LightProjectDescriptor = WithStdlibAndDependencyRustProjectDescriptor

    fun `test standard version`() = doTestByText("""
        [dependencies]
        base64 = "0.8.0"  # - Open documentation for `base64@0.8.0`

        [target.'cfg(unix)'.dependencies]
        base64 = "0.8.0"  # - Open documentation for `base64@0.8.0`
    """)

    fun `test platform specific`() = doTestByText("""
        [target.'cfg(unix)'.dependencies]
        base64 = "0.8.0"  # - Open documentation for `base64@0.8.0`
    """)

    fun `test standard detailed`() = doTestByText("""
        [dependencies]
        jquery = { version = "1.0.2", optional = true }  # - Open documentation for `jquery@1.0.2`
    """)

    fun `test standard dev`() = doTestByText("""
        [dev-dependencies]
        libc = "0.2.33"  # - Open documentation for `libc@0.2.33`
    """)

    fun `test standard build`() = doTestByText("""
        [build-dependencies]
        libc = "0.2.33"  # - Open documentation for `libc@0.2.33`
    """)

    fun `test specific crate`() = doTestByText("""
        [dependencies.clap]  # - Open documentation for `clap@2.27.1`
        version = "2.27.1"
    """)

    fun `test no link to doc`() = doTestByText("""
        [dependencies]
        hello_utils = { path = "hello_utils" }
    """)
}
