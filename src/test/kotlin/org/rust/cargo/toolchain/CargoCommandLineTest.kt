/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.toolchain

import org.junit.Assert
import org.junit.Test
import java.nio.file.Paths

class CargoCommandLineTest {
    @Test
    fun `getRunArguments should return an empty array if no arguments`() {
        val cot = CargoCommandLine("run", wd, listOf("--bin", "someproj"))
        val actual = cot.executableArguments
        val expected = emptyList<String>()
        Assert.assertEquals(expected, actual)
    }

    @Test
    fun `getRunArguments should return an empty array if no arguments but arg indicator`() {
        val cot = CargoCommandLine("run", wd, listOf("--bin", "someproj", "--"))
        val actual = cot.executableArguments
        val expected = emptyList<String>()
        Assert.assertEquals(expected, actual)
    }

    @Test
    fun `getRunArguments should return an array if arguments`() {
        val cot = CargoCommandLine("run", wd, listOf("--bin", "someproj", "--", "arg1"))
        val actual = cot.executableArguments
        val expected = listOf("arg1")
        Assert.assertEquals(expected, actual)
    }

    @Test
    fun `getBuildArguments should return build arguments`() {
        val expected = listOf("--bin", "someproj")
        val cot = CargoCommandLine("run", wd, expected)
        val actual = cot.subcommandArguments
        Assert.assertEquals(expected, actual)
    }

    @Test
    fun `getBuildArguments should return build arguments no arguments but arg indicator`() {
        val cot = CargoCommandLine("run", wd, listOf("--bin", "someproj", "--"))
        val actual = cot.subcommandArguments
        val expected = listOf("--bin", "someproj")
        Assert.assertEquals(expected, actual)
    }

    @Test
    fun `getBuildArguments should return build arguments only preceeding arguments`() {
        val cot = CargoCommandLine("run", wd, listOf("--bin", "someproj", "--", "arg1"))
        val actual = cot.subcommandArguments
        val expected = listOf("--bin", "someproj")
        Assert.assertEquals(expected, actual)
    }

    /**
     * Returns the list of arguments after the "--". If there is no "--" returns an empty list.
     */
    private val CargoCommandLine.executableArguments: List<String>
        get() = splitOnDoubleDash().second

    /**
     * Returns the arguments before any "--" argument, intended for the "cargo build" command.
     */
    private val CargoCommandLine.subcommandArguments: List<String>
        get() = splitOnDoubleDash().first

    private val wd = Paths.get("/my-crate")
}
