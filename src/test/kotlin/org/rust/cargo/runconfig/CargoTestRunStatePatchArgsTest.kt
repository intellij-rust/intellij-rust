/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.runconfig

import com.intellij.util.execution.ParametersListUtil
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.rust.RsTestBase
import org.rust.cargo.toolchain.CargoCommandLine
import java.nio.file.Path
import java.nio.file.Paths

@RunWith(Parameterized::class)
class CargoTestRunStatePatchArgsTest(
    private val input: String,
    private val expected: String
) : RsTestBase() {
    private val wd: Path = Paths.get("/my-crate")

    @Test
    fun test() = assertEquals(
        ParametersListUtil.parse(expected),
        CargoTestRunState.patchArgs(
            CargoCommandLine("run", wd, ParametersListUtil.parse(input))
        )
    )

    companion object {
        @Parameterized.Parameters(name = "{index}: {0}")
        @JvmStatic
        fun data(): Collection<Array<String>> = listOf(
            arrayOf("", "--no-fail-fast -- --format=json -Z unstable-options --show-output"),
            arrayOf("foo", "foo --no-fail-fast -- --format=json -Z unstable-options --show-output"),
            arrayOf("foo bar", "foo bar --no-fail-fast -- --format=json -Z unstable-options --show-output"),
            arrayOf("--", "--no-fail-fast -- --format=json -Z unstable-options --show-output"),

            arrayOf("-- -Z unstable-options", "--no-fail-fast -- --format=json -Z unstable-options --show-output"),
            arrayOf("-- --format=json", "--no-fail-fast -- --format=json -Z unstable-options --show-output"),
            arrayOf("-- --format json", "--no-fail-fast -- --format json -Z unstable-options --show-output"),
            arrayOf("-- --format pretty", "--no-fail-fast -- --format json -Z unstable-options --show-output"),
            arrayOf("-- --format=pretty", "--no-fail-fast -- --format=json -Z unstable-options --show-output")
        )
    }
}
