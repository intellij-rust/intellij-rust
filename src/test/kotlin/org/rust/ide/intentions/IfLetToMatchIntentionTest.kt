/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.intentions

class IfLetToMatchIntentionTest : RsIntentionTestBase(IfLetToMatchIntention()) {
    fun `test simple`() = doAvailableTest("""
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

    fun `test simple else`() = doAvailableTest("""
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

    fun `test else if`() = doAvailableTest("""
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

    fun `test else if else`() = doAvailableTest("""
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

    fun `test trackback if`() = doAvailableTest("""
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

    fun `test apply on same target`() = doUnavailableTest("""
    fn main() {
        if let A(value) = x {

        } else if let B(value) = y {
            /*caret*/
        }
    }
    """)
}
