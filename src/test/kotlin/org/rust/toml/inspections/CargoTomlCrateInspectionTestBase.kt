/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.toml.inspections

import com.intellij.codeInspection.InspectionProfileEntry
import org.intellij.lang.annotations.Language
import org.junit.runner.RunWith
import org.rust.RsJUnit4TestRunner
import org.rust.cargo.CargoConstants
import org.rust.ide.annotator.RsAnnotationTestFixture
import org.rust.ide.experiments.RsExperiments
import org.rust.ide.inspections.RsInspectionsTestBase
import org.rust.openapiext.runWithEnabledFeatures
import org.rust.toml.crates.local.CargoRegistryCrate
import org.rust.toml.crates.local.withMockedCrates
import kotlin.reflect.KClass

@RunWith(RsJUnit4TestRunner::class)
abstract class CargoTomlCrateInspectionTestBase(
    inspectionClass: KClass<out InspectionProfileEntry>
) : RsInspectionsTestBase(inspectionClass) {
    override fun createAnnotationFixture(): RsAnnotationTestFixture<Unit> =
        RsAnnotationTestFixture(
            this,
            myFixture,
            inspectionClasses = listOf(inspectionClass),
            baseFileName = CargoConstants.MANIFEST_FILE
        )

    protected fun doTest(@Language("TOML") code: String, vararg crates: Pair<String, CargoRegistryCrate>) {
        runTest(crates.toList()) {
            myFixture.configureByText(CargoConstants.MANIFEST_FILE, code)
            myFixture.checkHighlighting()
        }
    }

    protected fun checkFix(
        fixName: String,
        @Language("TOML") before: String,
        @Language("TOML") after: String,
        vararg crates: Pair<String, CargoRegistryCrate>
    ) {
        runTest(crates.toList()) {
            checkFixByText(fixName, before, after, checkWeakWarn = true)
        }
    }

    private fun runTest(crates: List<Pair<String, CargoRegistryCrate>>, action: () -> Unit) {
        runWithEnabledFeatures(RsExperiments.CRATES_LOCAL_INDEX) {
            withMockedCrates(crates.toMap()) {
                action()
            }
        }
    }
}
