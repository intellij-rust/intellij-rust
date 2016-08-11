package org.rust.ide.intentions

import org.rust.lang.RustTestCaseBase

class DemorgansLawIntentionTest : RustTestCaseBase() {
    override val dataPath = "org/rust/ide/intentions/fixtures/demorgan_law/"

    private fun doTest() = checkByFile {
        openFileInEditor(fileName)
        myFixture.launchAction(DemorgansLawIntention())
    }

    fun testOr() = doTest()
    fun testOrNot() = doTest()
    fun testNotOr() = doTest()
    fun testComplex1() = doTest()
    fun testReverseComplex1() = doTest()
    fun testComplex2() = doTest()
    fun testReverseComplex2() = doTest()
    fun testComplex3() = doTest()
    fun testReverseComplex3() = doTest()
    fun testComplex4() = doTest()
    fun testReverseComplex4() = doTest()
}
