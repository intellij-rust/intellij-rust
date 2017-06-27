/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.surroundWith.expression

import org.rust.ide.surroundWith.RsSurrounderTestBase

class RsWithWhileExpSurrounderTest : RsSurrounderTestBase(RsWithWhileExpSurrounder()) {
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

    fun testCorrectPostProcess() = doTest(
        """
        fn main() {
            <selection>true</selection>
            1;
        }
        """
        ,
        """
        fn main() {
            while true {<caret>}
            1;
        }
        """
    )

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
