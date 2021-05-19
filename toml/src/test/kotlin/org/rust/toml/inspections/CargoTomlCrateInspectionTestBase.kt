/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.toml.inspections

import com.intellij.codeInspection.InspectionProfileEntry
import org.intellij.lang.annotations.Language
import org.rust.cargo.CargoConstants
import org.rust.ide.experiments.RsExperiments
import org.rust.ide.inspections.RsInspectionsTestBase
import org.rust.openapiext.runWithEnabledFeatures
import org.rust.toml.crates.local.CargoRegistryCrate
import org.rust.toml.crates.local.withMockedCrates
import kotlin.reflect.KClass

abstract class CargoTomlCrateInspectionTestBase(
    inspectionClass: KClass<out InspectionProfileEntry>
) : RsInspectionsTestBase(inspectionClass) {
    protected fun doTest(@Language("TOML") code: String, vararg crates: Pair<String, CargoRegistryCrate>) {
        myFixture.configureByText(CargoConstants.MANIFEST_FILE, code)

        runWithEnabledFeatures(RsExperiments.CRATES_LOCAL_INDEX) {
            withMockedCrates(crates.toMap()) {
                myFixture.checkHighlighting()
            }
        }
    }
}
