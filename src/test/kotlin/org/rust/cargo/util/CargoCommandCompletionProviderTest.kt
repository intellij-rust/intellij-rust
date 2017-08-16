/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.util

import com.intellij.codeInsight.completion.PlainPrefixMatcher
import org.rust.lang.RsTestBase


class CargoCommandCompletionProviderTest : RsTestBase() {
    fun `test complete empty`() = checkCompletion(
        "",
        listOf("build", "check", "clean", "doc", "run", "test", "bench", "update", "search", "publish", "install")
    )

    fun `test complete command name`() = checkCompletion(
        "b",
        listOf("build", "bench")
    )

    private fun checkCompletion(
        text: String,
        expectedCompletions: List<String>
    ) {
        val provider = CargoCommandCompletionProvider(null)
        val prefix = provider.getPrefix(text, text.length)
        val matcher = PlainPrefixMatcher(prefix)

        val actual = provider.complete(text).filter { matcher.isStartMatch(it) }.map { it.lookupString }
        check(actual == expectedCompletions) {
            "\nExpected:\n$expectedCompletions\n\nActual:\n$actual"
        }
    }

}
