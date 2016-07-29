package org.rust.ide.surroundWith

class RustWithNotSurrounderTest : RustSurrounderTestCaseBase(RustWithNotSurrounder()) {
    fun testSimple() = doTest()
    fun testCall() = doTest()

    fun testNumber() = doTestNotApplicable()
    fun testNumberCall() = doTestNotApplicable()
}
