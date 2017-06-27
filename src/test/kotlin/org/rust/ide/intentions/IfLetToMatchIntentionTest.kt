/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.intentions

class IfLetToMatchIntentionTest : RsIntentionTestBase(IfLetToMatchIntention()) {
    fun testSimple() = doAvailableTest("""
    fn main() {
        if let Some(value) = x {/*caret*/

        }
    }
    """, """
    fn main() {
        match x {
            Some(value) => {}
        }
    }
    """)

    fun testSimpleElse() = doAvailableTest("""
    fn main() {
        if let Some(val) = x {

        } else {
            /*caret*/
        }
    }
    """, """
    fn main() {
        match x {
            Some(val) => {},
            _ => {}
        }
    }
    """)

    fun testElseIf() = doAvailableTest("""
    fn main() {
        if let A(value) = x {

        } else if let B(value) = x {
            /*caret*/
        }
    }
    """, """
    fn main() {
        match x {
            A(value) => {},
            B(value) => {}
        }
    }
    """)

    fun testElseIfElse() = doAvailableTest("""
    fn main() {
        if let A(value) = x {
            /*caret*/
        } else if let B(value) = x {

        } else {

        }
    }
    """, """
    fn main() {
        match x {
            A(value) => {},
            B(value) => {},
            _ => {}
        }
    }
    """
    )

    fun testTrackbackIf() = doAvailableTest("""
    fn main() {
        if let A(value) = x {

        } else if let B(value) = x {
            /*caret*/
        } else {

        }
    }
    """, """
    fn main() {
        match x {
            A(value) => {},
            B(value) => {},
            _ => {}
        }
    }
    """
    )

    fun testApplyOnSametarget() = doUnavailableTest("""
    fn main() {
        if let A(value) = x {

        } else if let B(value) = y {
            /*caret*/
        }
    }
    """)
}
