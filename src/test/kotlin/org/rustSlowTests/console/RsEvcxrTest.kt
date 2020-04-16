/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rustSlowTests.console

import org.rust.MinRustcVersion
import org.rust.cargo.RsWithToolchainTestBase
import org.rust.cargo.project.settings.toolchain
import org.rust.cargo.toolchain.Cargo
import org.rust.ide.console.RsConsoleCommunication
import org.rust.openapiext.pathAsPath
import java.io.PrintWriter
import java.util.*

@MinRustcVersion("1.40.0")  // Evcxr supports 1.40 and above
class RsEvcxrTest : RsWithToolchainTestBase() {

    override fun runTest() {
        if (Cargo.checkNeedInstallEvcxr(project)) {
            System.err.println("SKIP \"$name\": Evcxr is not installed")
            return
        }

        super.runTest()
    }

    fun `test process output format`() {
        val process = createProcess()
        val scanner = Scanner(process.inputStream)
        val writer = PrintWriter(process.outputStream)

        try {
            scanner.nextLine()  // "Welcome to evcxr. For help, type :help"
            expectPrompt(scanner, true)

            // success command
            writer.println("1 + 2")
            writer.flush()
            assertEquals(scanner.nextLine(), "3")
            expectPrompt(scanner, true)

            // fail command
            writer.println("let foo = bar;")
            writer.flush()
            scanner.nextLine()  // "not found in this scope"
            scanner.nextLine()  // "cannot find value `bar` in this scope"
            expectPrompt(scanner, false)
        } finally {
            process.destroy()
        }
    }

    private fun createProcess(): Process {
        val workingDirectory = cargoProjectDirectory.pathAsPath.toFile()
        val commandLine = project.toolchain!!.evcxr()!!.createCommandLine(workingDirectory)
        return commandLine.createProcess()
    }

    private fun expectPrompt(scanner: Scanner, success: Boolean) {
        val prompt = ">> "
        val promptColored = "\u001B[33m$prompt\u001B[0m"

        val markerExpected = if (success) {
            RsConsoleCommunication.SUCCESS_EXECUTION_MARKER
        } else {
            RsConsoleCommunication.FAILED_EXECUTION_MARKER
        }
        val markerReceived = scanner.nextChar().toString()
        assertEquals(markerExpected, markerReceived)

        expectInput(scanner, promptColored)
    }

    private fun expectInput(scanner: Scanner, string: String) {
        for (charExpected in string) {
            val charReceived = scanner.nextChar()
            assertEquals(charExpected, charReceived)
        }
    }

    private fun Scanner.nextChar(): Char {
        val oldDelimiter = delimiter()
        useDelimiter("")
        try {
            val char = next()
            check(char.length == 1)
            return char[0]
        } finally {
            useDelimiter(oldDelimiter)
        }
    }
}
