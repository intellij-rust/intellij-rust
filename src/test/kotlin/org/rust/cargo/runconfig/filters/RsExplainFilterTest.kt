package org.rust.cargo.runconfig.filters

import com.intellij.execution.filters.Filter
import org.assertj.core.api.Assertions.assertThat
import org.rust.lang.RsTestBase

class RsExplainFilterTest : RsTestBase() {
    private val filter: Filter get() = RsExplainFilter()

    fun testOldExplainFormat() {
        val text = "src/lib.rs:57:17: 57:25 help: run `rustc --explain E0282` to see a detailed explanation"
        doTest(text, text.length, 41, 56)
    }

    fun testNewExplainFormat() {
        val text = "error: the trait bound `std::string::String: std::ops::Index<_>` is not satisfied [--explain E0277]"
        doTest(text, text.length, 83, 98)
    }

    fun testErrorFormat() {
        val text = "error[E0382]: use of moved value: `v`"
        doTest(text, text.length, 5, 12)
    }

    fun testWarningFormat() {
        val text = "warning[E0122]: trait bounds are not (yet) enforced in type definitions"
        doTest(text, text.length, 7, 14)
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
        assertThat(item.getHyperlinkInfo()).isNotNull()
    }
}
