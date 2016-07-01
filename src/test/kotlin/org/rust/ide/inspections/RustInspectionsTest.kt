package org.rust.ide.inspections

class RustInspectionsTest : RustInspectionsTestBase() {

    override val dataPath = "org/rust/ide/inspections/fixtures"

    fun testApproxConstant() = doTest<RustApproxConstantInspection>()
    fun testSelfConvention() = doTest<RustSelfConventionInspection>()
}
