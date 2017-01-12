package org.rust.ide.surroundWith.expression

import org.rust.ide.surroundWith.RustSurrounderTestCaseBase

class RustWithIfExpSurrounderTest : RustSurrounderTestCaseBase(RustWithIfExpSurrounder()) {
    fun testSimple() {
        doTest(
            """
            fn main() {
                <selection>true</selection>
            }
            """
            ,
            """
            fn main() {
                if true {<caret>}
            }
            """
        )
    }

    fun testCall() {
        doTest(
            """
            fn func() -> bool {
                false
            }

            fn main() {
                <selection>func()</selection>
            }
            """
            ,
            """
            fn func() -> bool {
                false
            }

            fn main() {
                if func() {<caret>}
            }
            """
        )
    }

    fun testCorrectPostProcess() {
        doTest(
            """
            fn main() {
                <selection>true</selection>
                1;
            }
            """
            ,
            """
            fn main() {
                if true {<caret>}
                1;
            }
            """
        )
    }

    fun testNumber() {
        doTestNotApplicable(
            """
            fn main() {
                <selection>1234</selection>
            }
            """
        )
    }

    fun testNumberCall() {
        doTestNotApplicable(
            """
            fn func() -> i32 {
                1234
            }

            fn main() {
                <selection>func()</selection>
            }
            """
        )
    }
}
