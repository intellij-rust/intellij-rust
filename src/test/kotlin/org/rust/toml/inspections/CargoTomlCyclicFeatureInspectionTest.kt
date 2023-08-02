/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.toml.inspections

import org.intellij.lang.annotations.Language
import org.junit.runner.RunWith
import org.rust.RsJUnit4TestRunner
import org.rust.cargo.CargoConstants.MANIFEST_FILE
import org.rust.ide.inspections.RsInspectionsTestBase

@RunWith(RsJUnit4TestRunner::class)
class CargoTomlCyclicFeatureInspectionTest : RsInspectionsTestBase(CargoTomlCyclicFeatureInspection::class) {
    fun `test feature depending on itself`() = doTest("""
        [features]
        foo = [<error descr="Cyclic feature dependency: feature `foo` depends on itself">"foo"</error>]
    """)

    fun `test features depending on themselves`() = doTest("""
        [features]
        foo = ["bar"]
        bar = ["foo"]
    """)

    fun `test features depending acyclically`() = doTest("""
        [features]
        foo = []
        bar = ["foo"]
    """)

    private fun doTest(@Language("TOML") code: String) {
        myFixture.configureByText(MANIFEST_FILE, code)
        myFixture.checkHighlighting()
    }
}
