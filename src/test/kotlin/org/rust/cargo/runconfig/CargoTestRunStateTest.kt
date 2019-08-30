/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.runconfig

import com.intellij.util.execution.ParametersListUtil
import com.intellij.util.text.SemVer
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.rust.cargo.toolchain.CargoCommandLine
import org.rust.cargo.toolchain.RustChannel
import org.rust.cargo.toolchain.RustcVersion
import java.nio.file.Path
import java.nio.file.Paths
import java.time.LocalDate

@RunWith(Parameterized::class)
class CargoTestRunStatePatchArgsTest(
    private val input: String,
    private val expected: String
) {
    private val wd: Path = Paths.get("/my-crate")

    @Test
    fun `test without show output`() = assertEquals(
        ParametersListUtil.parse(expected),
        CargoTestRunState.patchArgs(
            CargoCommandLine("run", wd, ParametersListUtil.parse(input)),
            RustcVersion(
                SemVer.parseFromText("1.38.0")!!,
                "x86_64-unknown-linux-gnu",
                RustChannel.STABLE
            )
        )
    )

    @Test
    fun `test with show output`() = assertEquals(
        ParametersListUtil.parse(expected) + "--show-output",
        CargoTestRunState.patchArgs(
            CargoCommandLine("run", wd, ParametersListUtil.parse(input)),
            RustcVersion(
                SemVer.parseFromText("1.39.0")!!,
                "x86_64-unknown-linux-gnu",
                RustChannel.STABLE
            )
        )
    )

    companion object {
        @Parameterized.Parameters(name = "{index}: {0}")
        @JvmStatic
        fun data(): Collection<Array<String>> = listOf(
            arrayOf("", "--no-fail-fast -- -Z unstable-options --format=json"),
            arrayOf("foo", "foo --no-fail-fast -- -Z unstable-options --format=json"),
            arrayOf("foo bar", "foo bar --no-fail-fast -- -Z unstable-options --format=json"),
            arrayOf("--", "--no-fail-fast -- -Z unstable-options --format=json"),

            arrayOf("-- -Z unstable-options", "--no-fail-fast -- -Z unstable-options --format=json"),
            arrayOf("-- --format=json", "--no-fail-fast -- --format=json -Z unstable-options"),
            arrayOf("-- --format json", "--no-fail-fast -- --format json -Z unstable-options"),
            arrayOf("-- --format pretty", "--no-fail-fast -- --format json -Z unstable-options"),
            arrayOf("-- --format=pretty", "--no-fail-fast -- --format=json -Z unstable-options")
        )
    }
}

class CargoTestRunStatePatchArgsShowOutputEnableTest {

    private fun patchArgs(version: String, channel: RustChannel, date: LocalDate) = CargoTestRunState.patchArgs(
        CargoCommandLine("run", Paths.get("/my-crate"), ParametersListUtil.parse("")),
        RustcVersion(
            SemVer.parseFromText(version)!!,
            "x86_64-unknown-linux-gnu",
            channel,
            null,
            date
        )
    )

    // --show-output should be enabled on rustc 1.39 nightly and dev builds with a build date >= 2019-08-28
    // and on all stable and beta versions >= 1.39
    @Test
    fun `old versions should never use --show-output`() {
        assertFalse(
            patchArgs("1.38.0", RustChannel.STABLE, LocalDate.of(2019, 12, 31))
                .contains("--show-output")
        )
        assertFalse(
            patchArgs("1.38.0", RustChannel.NIGHTLY, LocalDate.of(2019, 12, 31))
                .contains("--show-output")
        )
        assertFalse(
            patchArgs("1.38.0", RustChannel.BETA, LocalDate.of(2019, 12, 31))
                .contains("--show-output")
        )
        assertFalse(
            patchArgs("1.39.0", RustChannel.NIGHTLY, LocalDate.of(2019, 8, 27))
                .contains("--show-output")
        )
    }

    @Test
    fun `new versions should use --show-output`() {
        // beta and stable always has the option, ignoring build date
        assertTrue(
            patchArgs("1.39.0", RustChannel.STABLE, LocalDate.of(2019, 7, 28))
                .contains("--show-output")
        )
        assertTrue(
            patchArgs("1.39.0", RustChannel.BETA, LocalDate.of(2019, 7, 28))
                .contains("--show-output")
        )
        // nightly and dev only if it's new enough
        assertTrue(
            patchArgs("1.39.0", RustChannel.NIGHTLY, LocalDate.of(2019, 8, 28))
                .contains("--show-output")
        )
        assertTrue(
            patchArgs("1.39.0", RustChannel.DEV, LocalDate.of(2019, 8, 28))
                .contains("--show-output")
        )
    }
}
