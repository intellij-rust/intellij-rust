/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.toml.completion

import org.intellij.lang.annotations.Language
import org.rust.ide.experiments.RsExperiments
import org.rust.openapiext.runWithEnabledFeatures
import org.rust.toml.crates.local.CargoRegistryCrate
import org.rust.toml.crates.local.withMockedCrates

abstract class LocalCargoTomlCompletionTestBase : CargoTomlCompletionTestBase() {
    protected fun doSingleCompletion(
        @Language("TOML") code: String,
        @Language("TOML") after: String,
        vararg crates: Pair<String, CargoRegistryCrate>
    ) {
        runWithEnabledFeatures(RsExperiments.CRATES_LOCAL_INDEX) {
            withMockedCrates(crates.toMap()) {
                completionFixture.doSingleCompletion(code, after)
            }
        }
    }

    @Suppress("SameParameterValue")
    protected fun checkNoCompletion(
        @Language("TOML") code: String,
        vararg crates: Pair<String, CargoRegistryCrate>
    ) {
        runWithEnabledFeatures(RsExperiments.CRATES_LOCAL_INDEX) {
            withMockedCrates(crates.toMap()) {
                completionFixture.checkNoCompletion(code)
            }
        }
    }

    @Suppress("SameParameterValue")
    protected fun checkNotContainsCompletion(
        variant: String,
        @Language("TOML") code: String,
        vararg crates: Pair<String, CargoRegistryCrate>
    ) {
        runWithEnabledFeatures(RsExperiments.CRATES_LOCAL_INDEX) {
            withMockedCrates(crates.toMap()) {
                completionFixture.checkNotContainsCompletion(code, setOf(variant))
            }
        }
    }

    @Suppress("SameParameterValue")
    protected fun doFirstCompletion(
        @Language("TOML") before: String,
        @Language("TOML") after: String,
        vararg crates: Pair<String, CargoRegistryCrate>
    ) {
        runWithEnabledFeatures(RsExperiments.CRATES_LOCAL_INDEX) {
            withMockedCrates(crates.toMap()) {
                completionFixture.doFirstCompletion(before, after)
            }
        }
    }

    protected fun checkCompletion(
        lookupString: String,
        @Language("TOML") before: String,
        @Language("TOML") after: String,
        vararg crates: Pair<String, CargoRegistryCrate>,
        completionChar: Char = '\n'
    ) {
        runWithEnabledFeatures(RsExperiments.CRATES_LOCAL_INDEX) {
            withMockedCrates(crates.toMap()) {
                completionFixture.checkCompletion(lookupString, before, after, completionChar)
            }
        }
    }

    @Suppress("SameParameterValue")
    protected fun completeBasic(
        @Language("TOML") code: String,
        expected: List<String>,
        vararg crates: Pair<String, CargoRegistryCrate>
    ) {
        runWithEnabledFeatures(RsExperiments.CRATES_LOCAL_INDEX) {
            withMockedCrates(crates.toMap()) {
                myFixture.configureByText("Cargo.toml", code)
                val actual = myFixture.completeBasic().map { it.lookupString }
                assertEquals(expected, actual)
            }
        }
    }
}
