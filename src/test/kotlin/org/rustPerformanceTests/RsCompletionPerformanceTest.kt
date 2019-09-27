/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rustPerformanceTests

import org.rust.ProjectDescriptor
import org.rust.WithStdlibRustProjectDescriptor
import org.rust.lang.core.completion.RsCompletionTestBase
import org.rust.stdext.Timings
import org.rust.stdext.repeatBenchmark

class RsCompletionPerformanceTest : RsCompletionTestBase() {
    override fun isPerformanceTest(): Boolean = false

    fun `test completion`() = repeatTest { timings ->
        myFixture.configureByText("main.rs", text)
        timings.measure("simple_completion") {
            myFixture.completeBasicAllCarets(null)
        }
    }

    @ProjectDescriptor(WithStdlibRustProjectDescriptor::class)
    fun `test completion with stdlib`() = repeatTest { timings ->
        myFixture.configureByText("main.rs", text)
        timings.measure("completion_from_index") {
            myFixture.completeBasicAllCarets(null)
        }
    }

    private fun repeatTest(f: (Timings) -> Unit) {
        repeatBenchmark {
            f(it)
            tearDown()
            setUp()
        }
    }

    companion object {
        private val text = (('a'..'z') + ('A'..'Z')).joinToString(
            separator = "",
            prefix = "fn main() {",
            postfix = "}"
        ) { "$it<caret>;" }
    }
}
