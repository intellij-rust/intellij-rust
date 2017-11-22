/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.runconfig.filters

import com.intellij.execution.filters.Filter
import org.rust.lang.RsTestBase

class RsExplainFilterTest : RsTestBase() {
    private val filter: Filter get() = RsExplainFilter()

    fun `test old explain format`() {
        val text = "src/lib.rs:57:17: 57:25 help: run `rustc --explain E0282` to see a detailed explanation"
        doTest(text, text.length, 41, 56)
    }

    fun `test new explain format`() {
        val text = "error: the trait bound `std::string::String: std::ops::Index<_>` is not satisfied [--explain E0277]"
        doTest(text, text.length, 83, 98)
    }

    fun `test error format`() {
        val text = "error[E0382]: use of moved value: `v`"
        doTest(text, text.length, 5, 12)
    }

    fun `test warning format`() {
        val text = "warning[E0122]: trait bounds are not (yet) enforced in type definitions"
        doTest(text, text.length, 7, 14)
    }

    fun `test nothing to see`() {
        val text = "src/lib.rs:57:17: 57:25 error: unable to infer enough type information about `_`; type annotations or generic parameter binding required [E0282]"
        check(filter.applyFilter(text, text.length) == null)
    }

    private fun doTest(line: String, entireLength: Int, highlightingStartOffset: Int, highlightingEndOffset: Int) {
        val result = checkNotNull(filter.applyFilter(line, entireLength)) {
            "No match in $line"
        }

        val item = result.resultItems.single()
        check(item.getHighlightStartOffset() == highlightingStartOffset)
        check(item.getHighlightEndOffset() == highlightingEndOffset)
        check(item.getHyperlinkInfo() != null)
    }
}
