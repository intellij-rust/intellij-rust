package org.rust.ide.surroundWith

class RustWithNotSurrounderTest : RustSurrounderTestCaseBase(RustWithNotSurrounder()) {
    fun testSimple() = doTest()
    // FIXME: Broken because of #371: https://github.com/intellij-rust/intellij-rust/issues/371
//    fun testCall() = doTest()

    fun testNumber() = doTestNotApplicable()
    fun testNumberCall() = doTestNotApplicable()
}
