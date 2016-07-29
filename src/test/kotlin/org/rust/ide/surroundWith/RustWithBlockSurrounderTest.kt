package org.rust.ide.surroundWith

class RustWithBlockSurrounderTest : RustSurrounderTestCaseBase(RustWithBlockSurrounder()) {
    fun testSimple() = doTest()
    fun testSimple2() = doTest()
    fun testSimple3() = doTest()
    fun testComments() = doTest()

    fun testAttrs() = doTestNotApplicable()
    fun testSimple4() = doTestNotApplicable()
}
