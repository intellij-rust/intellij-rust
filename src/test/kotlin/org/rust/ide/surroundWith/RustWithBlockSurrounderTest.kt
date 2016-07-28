package org.rust.ide.surroundWith

class RustWithBlockSurrounderTest : RustSurrounderTestCaseBase(RustWithBlockSurrounder()) {
    fun testSimple() = doTest()
    fun testSimple2() = doTest()
    fun testSimple3() = doTest()
    fun testComments() = doTest()

    fun testAttrs() = doTest(isApplicable = false)
    fun testSimple4() = doTest(isApplicable = false)
}
