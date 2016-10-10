package org.rust.cargo.runconfig

import com.intellij.execution.filters.OpenFileHyperlinkInfo
import com.intellij.openapi.vfs.VirtualFile
import org.assertj.core.api.Assertions
import org.rust.ide.utils.runWriteAction
import org.rust.lang.RustTestCaseBase

/**
 * Base class for tests of output highlighting filters.
 */
abstract class HighlightFilterTestBase : RustTestCaseBase() {
    override val dataPath = ""
    lateinit var projectDir: VirtualFile

    override fun setUp() {
        super.setUp()
        projectDir = createTestDirectoryAndFile()
    }

    protected fun doTest(filter: RegexpFileLinkFilter, line: String, entireLength: Int, hStart: Int, hEnd: Int) {
        val result = checkNotNull(filter.applyFilter(line, entireLength)) {
            "No match in $line"
        }

        val item = result.resultItems.single()
        Assertions.assertThat(item.getHighlightStartOffset()).isEqualTo(hStart)
        Assertions.assertThat(item.getHighlightEndOffset()).isEqualTo(hEnd)
        val hyperlink = checkNotNull(item.getHyperlinkInfo()) {
            "No hyperlink info"
        }
        val file = requireNotNull((hyperlink as OpenFileHyperlinkInfo).descriptor?.file)
        Assertions.assertThat(file.name).isEqualTo("main.rs")

    }

    private fun createTestDirectoryAndFile(): VirtualFile = runWriteAction {
        val baseDir = myFixture.tempDirFixture.findOrCreateDir("consoleFilterTest")
        baseDir.createChildDirectory(this, "src").createChildData(this, "main.rs")
        baseDir
    }
}
