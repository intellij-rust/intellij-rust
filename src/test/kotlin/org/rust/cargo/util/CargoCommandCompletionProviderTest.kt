/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.util

import com.intellij.codeInsight.completion.PlainPrefixMatcher
import org.rust.lang.RsTestBase


class CargoCommandCompletionProviderTest : RsTestBase() {
    fun `test split context prefix`() {
        val provider = CargoCommandCompletionProvider(null)
        fun doCheck(text: String, ctx: String, prefix: String) {
            val (actualCtx, actualPrefix) = provider.splitContextPrefix(text)
            check(actualCtx == ctx && actualPrefix == prefix) {
                "\nExpected\n\n$ctx, $prefix\nActual:\n$actualCtx, $actualPrefix"
            }
        }

        doCheck("build", "", "build")
        doCheck("b", "", "b")
        doCheck("build ", "build", "")
    }

    fun `test complete empty`() = checkCompletion(
        "",
        listOf("run", "test", "check", "build", "update", "bench", "doc", "publish", "clean", "search", "install")
    )

    fun `test complete command name`() = checkCompletion(
        "b",
        listOf("build", "bench")
    )

    fun `test no completion for unknown command`() = checkCompletion(
        "frob ",
        listOf()
    )

    fun `test complete run args`() = checkCompletion(
        "run ",
        listOf("--release", "--jobs", "--features", "--all-features", "--no-default-features", "--triple", "--verbose", "--quite", "--bin", "--example", "--package")
    )

    fun `test dont suggest a flag twice`() = checkCompletion(
        "run --release --rel",
        listOf()
    )

    private fun checkCompletion(
        text: String,
        expectedCompletions: List<String>
    ) {
        val provider = CargoCommandCompletionProvider(null)
        val (ctx, prefix) = provider.splitContextPrefix(text)
        val matcher = PlainPrefixMatcher(prefix)

        val actual = provider.complete(ctx).filter { matcher.isStartMatch(it) }.map { it.lookupString }
        check(actual == expectedCompletions) {
            "\nExpected:\n$expectedCompletions\n\nActual:\n$actual"
        }
    }
}
