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

    protected fun checkContainsCompletion(vararg commands: String, variants: List<String>) {
        val previousCommands = commands.slice(0 until commands.size - 1)
        val lastCommand = commands.last()

        completionFixture.checkContainsCompletion(Before(previousCommands, lastCommand), variants)

        if (previousCommands.size == 1) {
            val allCommands = previousCommands[0] + lastCommand
            completionFixture.checkContainsCompletion(Before(listOf(""), allCommands), variants)
            completionFixture.checkContainsCompletion(Before(listOf(""), "if true { $allCommands }"), variants)
            completionFixture.checkContainsCompletion(Before(listOf(""), "fn main() { $allCommands }"), variants)
        }
    }

    protected fun checkSingleCompletion(previousCommands: String, lastCommandBefore: String, lastCommandAfter: String) {
        completionFixture.doSingleCompletion(Before(listOf(previousCommands), lastCommandBefore), lastCommandAfter)
    }
}
