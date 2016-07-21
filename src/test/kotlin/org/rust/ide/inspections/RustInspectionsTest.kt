package org.rust.ide.inspections

import org.rust.ide.inspections.duplicates.RustDuplicateStructFieldInspection

class RustInspectionsTest : RustInspectionsTestBase() {

    override val dataPath = "org/rust/ide/inspections/fixtures"

    fun testApproxConstant() = doTest<RustApproxConstantInspection>()
    fun testSelfConvention() = doTest<RustSelfConventionInspection>()

    fun testDuplicateField() = doTest<RustDuplicateStructFieldInspection>()
}
