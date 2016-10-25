package org.rust.ide.surroundWith.expression

import org.rust.ide.surroundWith.RustSurrounderTestCaseBase

class RustWithWhileExpSurrounderTest : RustSurrounderTestCaseBase(RustWithWhileExpSurrounder()) {
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
                while true {<caret>}
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
                while func() {<caret>}
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
