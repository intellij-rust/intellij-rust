package org.rust.cargo.runconfig

import backcompat.runWriteAction
import com.intellij.execution.filters.Filter
import com.intellij.execution.filters.OpenFileHyperlinkInfo
import com.intellij.openapi.vfs.VirtualFile
import org.assertj.core.api.Assertions
import org.rust.lang.RustTestCaseBase
import java.util.*

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

    protected fun checkNoHighlights(filter: Filter, text: String) {
        val items = filter.applyFilter(text, text.length)?.resultItems ?: return
        Assertions.assertThat(items.size).isEqualTo(0)
    }

    protected fun checkHighlights(filter: Filter, before: String, after: String, lineIndex: Int = 0) {
        val line = before.split('\n')[lineIndex]
        val result = checkNotNull(filter.applyFilter(line, before.length)) {
            "No match in `$before`"
        }
        var checkText = before
        val items = ArrayList(result.resultItems)
        items.sortByDescending { it.getHighlightEndOffset() }
        items.forEach { item ->
            val range = IntRange(item.getHighlightStartOffset(), item.getHighlightEndOffset() - 1)
            var itemText = before.substring(range)
            (item.getHyperlinkInfo() as? OpenFileHyperlinkInfo)?.let { link ->
                itemText = "$itemText -> ${link.descriptor?.file?.name}"
            }
            checkText = checkText.replaceRange(range, "[$itemText]")
        }
        checkText = checkText.split('\n')[lineIndex]
        Assertions.assertThat(checkText).isEqualTo(after)
    }

    private fun createTestDirectoryAndFile(): VirtualFile = runWriteAction {
        val baseDir = myFixture.tempDirFixture.findOrCreateDir("consoleFilterTest")
        baseDir.createChildDirectory(this, "src").createChildData(this, "main.rs")
        baseDir
    }
}
