/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.intentions

class MergeIfsIntentionTest : RsIntentionTestBase(MergeIfsIntention::class) {

    fun `test simple`() = doAvailableTest("""
        fn main() {
            /*caret*/if a {
                if b {
                    func();
                }
            }
        }
    """, """
        fn main() {
            if a && b {
                func();
            }
        }
    """)

    fun `test disjunction condition 1`() = doAvailableTest("""
        fn main() {
            /*caret*/if a1 || a2 {
                if b {
                    func();
                }
            }
        }
    """, """
        fn main() {
            if (a1 || a2) && b {
                func();
            }
        }
    """)

    fun `test disjunction condition 2`() = doAvailableTest("""
        fn main() {
            /*caret*/if a {
                if b1 || b2 {
                    func();
                }
            }
        }
    """, """
        fn main() {
            if a && (b1 || b2) {
                func();
            }
        }
    """)

    fun `test conjunction conditions`() = doAvailableTest("""
        fn main() {
            /*caret*/if a1 && a2 {
                if b1 && b2 {
                    func();
                }
            }
        }
    """, """
        fn main() {
            if a1 && a2 && b1 && b2 {
                func();
            }
        }
    """)

    fun `test availability range`() = checkAvailableInSelectionOnly("""
        fn main() {
            <selection>if</selection> a {
                if b {
                    func();
                }
            }
        }
    """)

    fun `test requires nested if`() = doUnavailableTest("""
        fn main() {
            /*caret*/if a {}
        }
    """)

    fun `test requires single nested if`() = doUnavailableTest("""
        fn main() {
            /*caret*/if a {
                if b {}
                if c {}
            }
        }
    """)

    fun `test requires no else 1`() = doUnavailableTest("""
        fn main() {
            /*caret*/if a {
                if b {}
            } else {}
        }
    """)

    fun `test requires no else 2`() = doUnavailableTest("""
        fn main() {
            /*caret*/if a {
                if b {} else {}
            }
        }
    """)

    fun `test requires simple condition`() = doUnavailableTest("""
        fn main() {
            /*caret*/if let x = 1 {
                if b {} else {}
            }
        }
    """)
}
