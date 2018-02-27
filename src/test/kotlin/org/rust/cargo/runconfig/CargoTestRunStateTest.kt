/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.runconfig

import com.intellij.util.execution.ParametersListUtil
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class CargoTestRunStatePatchArgsTest(
    private val input: String,
    private val expected: String
) {
    @Test
    fun test() = assertEquals(
        ParametersListUtil.parse(expected),
        CargoTestRunState.patchArgs(ParametersListUtil.parse(input))
    )

    companion object {
        @Parameterized.Parameters(name = "{index}: {0}")
        @JvmStatic fun data(): Collection<Array<String>> = listOf(
            arrayOf("", "-- -Z unstable-options --format=json"),
            arrayOf("foo", "foo -- -Z unstable-options --format=json"),
            arrayOf("foo bar", "foo bar -- -Z unstable-options --format=json"),
            arrayOf("--", "-- -Z unstable-options --format=json"),

            arrayOf("-- -Z unstable-options", "-- -Z unstable-options --format=json"),
            arrayOf("-- --format=json", "-- --format=json -Z unstable-options"),
            arrayOf("-- --format json", "-- --format json -Z unstable-options"),
            arrayOf("-- --format pretty", "-- --format json -Z unstable-options"),
            arrayOf("-- --format=pretty", "-- --format=json -Z unstable-options")
        )
    }
}
