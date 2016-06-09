package org.rust.cargo.runconfig

import com.intellij.execution.filters.Filter
import com.intellij.execution.filters.OpenFileHyperlinkInfo
import com.intellij.openapi.vfs.VirtualFile
import org.assertj.core.api.Assertions.assertThat
import org.rust.ide.utils.runWriteAction
import org.rust.lang.RustTestCaseBase

class RustConsoleFilterTest : RustTestCaseBase() {
    override val dataPath = ""

    private lateinit var filter: Filter

    override fun setUp() {
        super.setUp()
        val dir = createTestDirectoryAndFile()
        filter = RustConsoleFilter(project, dir)
    }

    fun testTypeError() {
        val error = "src/main.rs:4:5: 4:12 error: this function takes 0 parameters but 1 parameter was supplied [E0061]\n"
        doTest(error, error.length, 0, 11)
    }

    fun testOffsetsForSeveralLines() {
        val text = """/home/user/.multirust/toolchains/beta/bin/cargo run
   Compiling rustraytracer v0.1.0 (file:///home/user/projects/rustraytracer)
src/main.rs:25:26: 25:40 error: no method named `read_to_string` found for type `core::result::Result<std::fs::File, std::io::error::Error>` in the current scope"""
        val line = text.split('\n')[2]
        doTest(line, text.length, 129, 140)
    }

    fun testNewErrorFormat() {
        val text = """error: the trait bound `std::string::String: std::ops::Index<_>` is not satisfied [--explain E0277]
 --> src/main.rs:4:5
 """
        val line = text.split('\n')[1]
        doTest(line, text.length, 107, 118)
    }

    private fun doTest(line: String, entireLength: Int, highlightingStartOffset: Int, highlightingEndOffset: Int) {
        val result = checkNotNull(filter.applyFilter(line, entireLength)) {
            "No match in $line"
        }

        val item = result.resultItems.single()
        assertThat(item.getHighlightStartOffset()).isEqualTo(highlightingStartOffset)
        assertThat(item.getHighlightEndOffset()).isEqualTo(highlightingEndOffset)
        val hyperlink = checkNotNull(item.getHyperlinkInfo())
        val file = requireNotNull((hyperlink as OpenFileHyperlinkInfo).descriptor?.file)
        assertThat(file.name).isEqualTo("main.rs")
    }

    private fun createTestDirectoryAndFile(): VirtualFile = runWriteAction {
        val baseDir = myFixture.tempDirFixture.findOrCreateDir("consoleFilterTest")
        baseDir.createChildDirectory(this, "src").createChildData(this, "main.rs")
        baseDir
    }
}
