package org.rust.ide.inspections

import org.rust.ide.inspections.duplicates.RustDuplicateStructFieldInspection
import org.rust.ide.inspections.duplicates.RustDuplicateTraitConstantInspection
import org.rust.ide.inspections.duplicates.RustDuplicateTraitMethodInspection
import org.rust.ide.inspections.duplicates.RustDuplicateTraitTypeInspection

class RustInspectionsTest : RustInspectionsTestBase() {

    override val dataPath = "org/rust/ide/inspections/fixtures"

    fun testApproxConstant() = doTest<RustApproxConstantInspection>()
    fun testSelfConvention() = doTest<RustSelfConventionInspection>()

    fun testDuplicateField() = doTest<RustDuplicateStructFieldInspection>()

    fun testDuplicateTraitConstant() = doTest<RustDuplicateTraitConstantInspection>()
    fun testDuplicateTraitMethod() = doTest<RustDuplicateTraitMethodInspection>()
    fun testDuplicateTraitType() = doTest<RustDuplicateTraitTypeInspection>()

    fun testSuppression() = checkByFile {
        enableInspection<RustSelfConventionInspection>()
        myFixture.checkHighlighting(true, false, true)
        applyQuickFix("Suppress for item")
        myFixture.testHighlighting(true, false, true, fileName.replace(".rs", "_after.rs"))
    }
}
