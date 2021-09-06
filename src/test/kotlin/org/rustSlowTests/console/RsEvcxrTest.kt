/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rustSlowTests.console

import com.intellij.util.ThrowableRunnable
import org.rust.cargo.RsWithToolchainTestBase
import org.rust.cargo.toolchain.tools.Cargo
import org.rust.cargo.toolchain.tools.evcxr
import org.rust.ide.console.RsConsoleCommunication
import org.rust.openapiext.pathAsPath
import java.io.PrintWriter
import java.util.*

class RsEvcxrTest : RsWithToolchainTestBase() {
    override fun runTestRunnable(testRunnable: ThrowableRunnable<Throwable>) {
        if (Cargo.checkNeedInstallEvcxr(project)) {
            System.err.println("SKIP \"$name\": Evcxr is not installed")
            return
        }
        super.runTestRunnable(testRunnable)
    }

    fun `test process output format`() {
        val process = createProcess()
        val scanner = Scanner(process.inputStream)
        val writer = PrintWriter(process.outputStream)

        try {
            scanner.nextLine()  // "Welcome to evcxr. For help, type :help"
            expectPrompt(scanner, true, withMarker = false)

            // success command
            writer.println("1 + 2")
            writer.flush()
            assertEquals(scanner.nextLine(), "3")
            expectPrompt(scanner, true)

            // success command
            writer.println("1 + 3")
            writer.flush()
            assertEquals(scanner.nextLine(), "4")
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
        val commandLine = rustupFixture.toolchain!!.evcxr()!!.createCommandLine(workingDirectory)
        return commandLine.createProcess()
    }

    private fun expectPrompt(scanner: Scanner, success: Boolean, withMarker: Boolean = true) {
        if (withMarker) {
            val markerExpected = if (success) {
                RsConsoleCommunication.SUCCESS_EXECUTION_MARKER
            } else {
                RsConsoleCommunication.FAILED_EXECUTION_MARKER
            }
            val markerReceived = scanner.nextChar().toString()
            assertEquals(markerExpected, markerReceived)
        }

        val prompt = ">> "
        val promptColored = "\u001B[33m$prompt\u001B[0m"
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
