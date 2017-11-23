/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.intentions

class UnwrapToTryIntentionTest : RsIntentionTestBase(UnwrapToTryIntention()) {
    fun `test available 1`() = doAvailableTest("""
        fn main() {
            let a = a.unwrap/*caret*/();
        }
    """, """
        fn main() {
            let a = a?;
        }
    """)

    fun `test available 2`() = doAvailableTest("""
        fn main() {
            let a = Ok(12).unwrap/*caret*/();
        }
    """, """
        fn main() {
            let a = Ok(12)?;
        }
    """)

    fun `test available 3`() = doAvailableTest("""
        fn main() {
            let a = (a + b).unwrap/*caret*/();
        }
    """, """
        fn main() {
            let a = (a + b)?;
        }
    """)

    fun `test available 4`() = doAvailableTest("""
        fn main() {
            let a = a + b.unwrap/*caret*/();
        }
    """, """
        fn main() {
            let a = a + b?;
        }
    """)

    fun `test available 5`() = doAvailableTest("""
        fn main() {
            let a = a.unwrap/*caret*/().to_string();
        }
    """, """
        fn main() {
            let a = a?.to_string();
        }
    """)

    fun `test available 6`() = doAvailableTest("""
        fn main() {
            let a = a.unwrap().unwrap/*caret*/().unwrap();
        }
    """, """
        fn main() {
            let a = a.unwrap()?.unwrap();
        }
    """)

    fun `test available 7`() = doAvailableTest("""
        fn main() {
            let a = a.unwrap  /*caret*/  ();
        }
    """, """
        fn main() {
            let a = a?;
        }
    """)

    fun `test available 8`() = doAvailableTest("""
        fn main() {
            let a = a.unwrap(b.unwrap(/*caret*/));
        }
    """, """
        fn main() {
            let a = a.unwrap(b?);
        }
    """)

    fun `test unavailable 1`() = doUnavailableTest("""
        fn main() {
            let a = a.foo/*caret*/();
        }
    """)

    fun `test unavailable 2`() = doUnavailableTest("""
        fn main() {
            let a = a.unwrap::<>/*caret*/();
        }
    """)

    fun `test unavailable 3`() = doUnavailableTest("""
        fn main() {
            let a = a.unwrap::<i32>/*caret*/();
        }
    """)

    fun `test unavailable 4`() = doUnavailableTest("""
        fn main() {
            let a = a.unwrap/*caret*/(12);
        }
    """)

    fun `test unavailable 5`() = doUnavailableTest("""
        fn main() {
            let a = a.unwrap/*caret*/;
        }
    """)
}
