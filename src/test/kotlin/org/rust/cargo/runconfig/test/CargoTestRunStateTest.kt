package org.rust.cargo.runconfig.test

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
            arrayOf("", "--no-fail-fast -- --nocapture --test-threads 1"),
            arrayOf("foo", "foo --no-fail-fast -- --nocapture --test-threads 1"),
            arrayOf("foo bar", "foo bar --no-fail-fast -- --nocapture --test-threads 1"),
            arrayOf("--", "--no-fail-fast -- --nocapture --test-threads 1"),

            arrayOf("-- --nocapture", "--no-fail-fast -- --nocapture --test-threads 1"),
            arrayOf("--no-fail-fast -- --nocapture", "--no-fail-fast -- --nocapture --test-threads 1"),
            arrayOf("--no-fail-fast -- --nocapture --test-threads", "--no-fail-fast -- --nocapture --test-threads 1"),
            arrayOf("--no-fail-fast -- --nocapture --test-threads 1", "--no-fail-fast -- --nocapture --test-threads 1"),
            arrayOf("--no-fail-fast -- --nocapture --test-threads 2", "--no-fail-fast -- --nocapture --test-threads 1"),
            arrayOf("--no-fail-fast -- --test-threads 2 --nocapture", "--no-fail-fast -- --test-threads 1 --nocapture"),
            arrayOf("--no-fail-fast -- --test-threads --nocapture", "--no-fail-fast -- --test-threads 1 --nocapture")
        )
    }
}
