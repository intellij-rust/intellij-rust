/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.toolchain

import com.intellij.testFramework.UsefulTestCase
import com.intellij.util.containers.EmptyListIterator
import org.junit.Assert
import org.junit.Test

class CargoCommandLineTest {
    @Test
    public fun `getRunArguments should return an empty array if no arguments`() {
        val cot = CargoCommandLine("run", listOf("--bin", "someproj"))
        val actual = cot.getRunArguments()
        val expected = emptyList<String>()
        Assert.assertEquals(expected, actual)
    }

    @Test
    public fun `getRunArguments should return an empty array if no arguments but arg indicator`() {
        val cot = CargoCommandLine("run", listOf("--bin", "someproj", "--"))
        val actual = cot.getRunArguments()
        val expected = emptyList<String>()
        Assert.assertEquals(expected, actual)
    }

    @Test
    public fun `getRunArguments should return an array if arguments`() {
        val cot = CargoCommandLine("run", listOf("--bin", "someproj", "--", "arg1"))
        val actual = cot.getRunArguments()
        val expected = listOf("arg1")
        Assert.assertEquals(expected, actual)
    }

    @Test
    public fun `getBuildArguments should return build arguments`() {
        val expected = listOf("--bin", "someproj")
        val cot = CargoCommandLine("run", expected)
        val actual = cot.getBuildArguments()
        Assert.assertEquals(expected, actual)
    }

    @Test
    public fun `getBuildArguments should return build arguments no arguments but arg indicator`() {
        val cot = CargoCommandLine("run", listOf("--bin", "someproj", "--"))
        val actual = cot.getBuildArguments()
        val expected = listOf("--bin", "someproj")
        Assert.assertEquals(expected, actual)
    }

    @Test
    public fun `getBuildArguments should return build arguments only preceeding arguments`() {
        val cot = CargoCommandLine("run", listOf("--bin", "someproj", "--", "arg1"))
        val actual = cot.getBuildArguments()
        val expected = listOf("--bin", "someproj")
        Assert.assertEquals(expected, actual)
    }
}
