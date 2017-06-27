/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.intentions

class UnwrapToTryIntentionTest : RsIntentionTestBase(UnwrapToTryIntention()) {
    fun testAvailable1() = doAvailableTest("""
        fn main() {
            let a = a.unwrap/*caret*/();
        }
    """, """
        fn main() {
            let a = a?;
        }
    """)

    fun testAvailable2() = doAvailableTest("""
        fn main() {
            let a = Ok(12).unwrap/*caret*/();
        }
    """, """
        fn main() {
            let a = Ok(12)?;
        }
    """)

    fun testAvailable3() = doAvailableTest("""
        fn main() {
            let a = (a + b).unwrap/*caret*/();
        }
    """, """
        fn main() {
            let a = (a + b)?;
        }
    """)

    fun testAvailable4() = doAvailableTest("""
        fn main() {
            let a = a + b.unwrap/*caret*/();
        }
    """, """
        fn main() {
            let a = a + b?;
        }
    """)

    fun testAvailable5() = doAvailableTest("""
        fn main() {
            let a = a.unwrap/*caret*/().to_string();
        }
    """, """
        fn main() {
            let a = a?.to_string();
        }
    """)

    fun testAvailable6() = doAvailableTest("""
        fn main() {
            let a = a.unwrap().unwrap/*caret*/().unwrap();
        }
    """, """
        fn main() {
            let a = a.unwrap()?.unwrap();
        }
    """)

    fun testAvailable7() = doAvailableTest("""
        fn main() {
            let a = a.unwrap  /*caret*/  ();
        }
    """, """
        fn main() {
            let a = a?;
        }
    """)

    fun testAvailable8() = doAvailableTest("""
        fn main() {
            let a = a.unwrap(b.unwrap(/*caret*/));
        }
    """, """
        fn main() {
            let a = a.unwrap(b?);
        }
    """)

    fun testUnavailable1() = doUnavailableTest("""
        fn main() {
            let a = a.foo/*caret*/();
        }
    """)

    fun testUnavailable2() = doUnavailableTest("""
        fn main() {
            let a = a.unwrap::<>/*caret*/();
        }
    """)

    fun testUnavailable3() = doUnavailableTest("""
        fn main() {
            let a = a.unwrap::<i32>/*caret*/();
        }
    """)

    fun testUnavailable4() = doUnavailableTest("""
        fn main() {
            let a = a.unwrap/*caret*/(12);
        }
    """)

    fun testUnavailable5() = doUnavailableTest("""
        fn main() {
            let a = a.unwrap/*caret*/;
        }
    """)
}
