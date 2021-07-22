/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.intentions

class JoinWildcardsIntentionTest : RsIntentionTestBase(JoinWildcardsIntention::class) {
    fun `test availability range`() = checkAvailableInSelectionOnly("""
        fn main() {
            let (first, <selection>_, _</selection>) = (1, 2, 3);
        }
    """)

    fun `test unavailable if already has dots`() = doUnavailableTest("""
        fn main() {
            let (/*caret*/_, _, middle, ..) = (1, 2, 3, 4);
        }
    """)

    fun `test trailing`() = doAvailableTest("""
        fn main() {
            let (first, _, _/*caret*/) = (1, 2, 3);
        }
    """, """
        fn main() {
            let (first, ../*caret*/) = (1, 2, 3);
        }
    """)

    fun `test leading`() = doAvailableTest("""
        fn main() {
            let (/*caret*/_, _, last) = (1, 2, 3);
        }
    """, """
        fn main() {
            let (../*caret*/, last) = (1, 2, 3);
        }
    """)

    fun `test middle`() = doAvailableTest("""
        fn main() {
            let (first, /*caret*/_, _, last) = (1, 2, 3, 4);
        }
    """, """
        fn main() {
            let (first, ../*caret*/, last) = (1, 2, 3, 4);
        }
    """)

    fun `test single '_'`() = doAvailableTest("""
        fn main() {
            let (/*caret*/_, middle) = (1, 2);
        }
    """, """
        fn main() {
            let (../*caret*/, middle) = (1, 2);
        }
    """)

    fun `test struct`() = doAvailableTest("""
        struct S(usize, usize, usize);

        fn main() {
            let S(first, _, _/*caret*/) = S(1, 2, 3);
        }
    """, """
        struct S(usize, usize, usize);

        fn main() {
            let S(first, ../*caret*/) = S(1, 2, 3);
        }
    """)

    fun `test slice`() = doAvailableTest("""
        fn main() {
            let [first, _, _/*caret*/] = [1, 2, 3];
        }
    """, """
        fn main() {
            let [first, ../*caret*/] = [1, 2, 3];
        }
    """)

    fun `test multiple wild pat runs 1`() = doAvailableTest("""
        fn main() {
            let [_, /*caret*/_, x, _, _] = [1, 2, 3, 4, 5];
        }
      """, """
        fn main() {
            let [../*caret*/, x, _, _] = [1, 2, 3, 4, 5];
        }
      """)

    fun `test multiple wild pat runs 2`() = doAvailableTest("""
        fn main() {
            let [_, _, x, _/*caret*/, _] = [1, 2, 3, 4, 5];
        }
      """, """
        fn main() {
            let [_, _, x, ../*caret*/] = [1, 2, 3, 4, 5];
        }
      """)
}
