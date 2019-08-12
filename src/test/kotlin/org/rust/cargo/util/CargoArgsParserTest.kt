/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.util

import com.intellij.util.execution.ParametersListUtil
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.rust.cargo.util.CargoArgsParser.Companion.parseArgs

@RunWith(Parameterized::class)
class CargoArgsParserTest(
    private val command: String,
    private val additionalArguments: String,
    private val expectedCommandArguments: String,
    private val expectedExecutableArguments: String
) {
    @Test
    fun test() = assertEquals(
        ParsedCargoArgs(
            ParametersListUtil.parse(expectedCommandArguments),
            ParametersListUtil.parse(expectedExecutableArguments)
        ),
        parseArgs(command, ParametersListUtil.parse(additionalArguments))
    )

    companion object {
        @Parameterized.Parameters(name = "{index}: {0}")
        @JvmStatic
        fun data(): Collection<Array<String>> = listOf(
            arrayOf("run", "a", "", "a"),
            arrayOf("run", "a b", "", "a b"),
            arrayOf("run", "-- a b", "", "a b"),
            arrayOf("run", "a -- b", "", "a -- b"),
            arrayOf("run", "a -- -- b", "", "a -- -- b"),
            arrayOf("run", "-- -- a", "", "-- a"),
            arrayOf("run", "--bin a", "--bin a", ""),
            arrayOf("run", "--bin a b", "--bin a", "b"),
            arrayOf("run", "--test a", "--test", "a"),
            arrayOf("run", "--features a b", "--features a b", ""),
            arrayOf("run", "--qwe a", "--qwe", "a"),

            arrayOf("test", "a", "a", "a"),
            arrayOf("test", "a b", "a b", "b"),
            arrayOf("test", "-- a b", "", "a b"),
            arrayOf("test", "a -- b", "a", "a b"),
            arrayOf("test", "a -- -- b", "a", "a -- b"),
            arrayOf("test", "-- -- a", "", "-- a"),
            arrayOf("test", "--bin a", "--bin a", ""),
            arrayOf("test", "--test a", "--test a", ""),
            arrayOf("test", "--bin a b", "--bin a b", "b"),
            arrayOf("test", "--features a b", "--features a b", ""),
            arrayOf("test", "--qwe a", "--qwe a", "a")
        )
    }
}
