package org.rust.cargo.runconfig

import com.intellij.execution.filters.Filter
import org.assertj.core.api.Assertions.assertThat
import org.rust.lang.RustTestCaseBase

class RustExplainFilterTest : RustTestCaseBase() {
    override val dataPath = ""
    private var filter: Filter = RustExplainFilter()

    fun testOldErrorFormat() {
        val text = "src/lib.rs:57:17: 57:25 help: run `rustc --explain E0282` to see a detailed explanation"
        doTest(text, text.length, 41, 56)
    }

    fun testNewErrorFormat() {
        val text = "error: the trait bound `std::string::String: std::ops::Index<_>` is not satisfied [--explain E0277]"
        doTest(text, text.length, 83, 98)
    }

    fun testNothingToSee() {
        val text = "src/lib.rs:57:17: 57:25 error: unable to infer enough type information about `_`; type annotations or generic parameter binding required [E0282]"
        assertThat(filter.applyFilter(text, text.length)).isNull()
    }

    private fun doTest(line: String, entireLength: Int, highlightingStartOffset: Int, highlightingEndOffset: Int) {
        val result = checkNotNull(filter.applyFilter(line, entireLength)) {
            "No match in $line"
        }

        val item = result.resultItems.single()
        assertThat(item.getHighlightStartOffset()).isEqualTo(highlightingStartOffset)
        assertThat(item.getHighlightEndOffset()).isEqualTo(highlightingEndOffset)
        assertThat(item.getHyperlinkInfo()).isNotNull();
    }
}
