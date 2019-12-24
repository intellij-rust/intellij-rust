/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rustSlowTests.console

import org.rust.cargo.RsWithToolchainTestBase
import org.rust.cargo.project.settings.toolchain
import org.rust.cargo.toolchain.Cargo
import java.io.PrintWriter
import java.util.*

class RsEvcxrTest : RsWithToolchainTestBase() {

    fun testProcessOutputFormat() {
        if (Cargo.checkNeedInstallEvcxr(project)) return

        val process = createProcess()
        val scanner = Scanner(process.inputStream)
        val writer = PrintWriter(process.outputStream)

        assertEquals(scanner.nextLine(), "Welcome to evcxr. For help, type :help")
        expectPrompt(scanner, true)

        // success command
        writer.println("1 + 2")
        writer.flush()
        assertEquals(scanner.nextLine(), "3")
        expectPrompt(scanner, true)

        // fail command
        writer.println("let foo = bar;")
        writer.flush()
        assert(scanner.nextLine().contains("not found in this scope"))
        assert(scanner.nextLine().contains("cannot find value `bar` in this scope"))
        expectPrompt(scanner, false)

        process.destroy()
    }

    private fun createProcess(): Process {
        val commandLine = project.toolchain!!.evcxr()!!.createCommandLine(null)
        return commandLine.createProcess()
    }

    private fun expectPrompt(scanner: Scanner, success: Boolean) {
        val prompt = ">> "
        val promptColored = "\u001B[33m$prompt\u001B[0m"

        val marker = if (success) {
            "\u0001"
        } else {
            "\u0002"
        }
        expectInput(scanner, marker)
        expectInput(scanner, promptColored)
    }

    private fun expectInput(scanner: Scanner, string: String) {
        val oldDelimiter = scanner.delimiter()
        scanner.useDelimiter("")

        for (charExpected in string) {
            val charReceived = scanner.next()[0]
            assertEquals(charExpected, charReceived)
        }

        scanner.useDelimiter(oldDelimiter)
    }
}
