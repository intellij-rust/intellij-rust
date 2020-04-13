/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.console

import org.rust.ProjectDescriptor
import org.rust.RsTestBase
import org.rust.WithStdlibRustProjectDescriptor
import org.rust.ide.console.RsConsoleCompletionTestFixture.Before

@ProjectDescriptor(WithStdlibRustProjectDescriptor::class)
abstract class RsConsoleCompletionTestBase : RsTestBase() {

    private lateinit var completionFixture: RsConsoleCompletionTestFixture

    override fun setUp() {
        super.setUp()
        completionFixture = RsConsoleCompletionTestFixture(myFixture)
        completionFixture.setUp()
    }

    override fun tearDown() {
        completionFixture.tearDown()
        super.tearDown()
    }

    protected fun checkContainsCompletion(previousLines: String, lastLine: String, variants: List<String>) {
        val previousLinesTrimmed = previousLines.trimIndent()
        val lastLineTrimmed = lastLine.trimIndent()

        completionFixture.checkContainsCompletion(Before(previousLinesTrimmed, lastLineTrimmed), variants)
        if (previousLines.isBlank()) return

        val lastLines = previousLinesTrimmed + lastLineTrimmed
        completionFixture.checkContainsCompletion(Before("", lastLines), variants)
        completionFixture.checkContainsCompletion(Before("", "if true { $lastLines }"), variants)
        completionFixture.checkContainsCompletion(Before("", "fn main() { $lastLines }"), variants)
    }

    protected fun checkSingleCompletion(previousLines: String, lastLineBefore: String, lastLineAfter: String) {
        completionFixture.doSingleCompletion(Before(previousLines, lastLineBefore), lastLineAfter)
    }
}
