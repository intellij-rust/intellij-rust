/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rustSlowTests

import org.rust.ProjectDescriptor
import org.rust.WithStdlibRustProjectDescriptor
import org.rust.lang.core.completion.RsCompletionTestBase
import org.rust.stdext.Timings

class RsCompletionPerformanceTest : RsCompletionTestBase() {
    override fun isPerformanceTest(): Boolean = false

    fun `test completion`() = repeatTest {
        val timings = Timings()
        myFixture.configureByText("main.rs", text)
        timings.measure("simple_completion") {
            myFixture.completeBasicAllCarets(null)
        }
        timings
    }

    @ProjectDescriptor(WithStdlibRustProjectDescriptor::class)
    fun `test completion with stdlib`() = repeatTest {
        val timings = Timings()
        myFixture.configureByText("main.rs", text)
        timings.measure("completion_from_index") {
            myFixture.completeBasicAllCarets(null)
        }
        timings
    }

    private fun repeatTest(f: () -> Timings) {
        var result = Timings()
        repeat(10) {
            result = result.merge(f())
            tearDown()
            setUp()
        }
        result.report()
    }

    companion object {
        private val text = (('a'..'z') + ('A'..'Z')).joinToString(
            separator = "",
            prefix = "fn main() {",
            postfix = "}"
        ) { "$it<caret>;" }
    }
}
