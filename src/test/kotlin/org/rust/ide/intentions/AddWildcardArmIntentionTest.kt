/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.intentions

/**
 * More tests for base functionality can be found in [org.rust.ide.inspections.match.RsNonExhaustiveMatchInspectionTest]
 */
class AddWildcardArmIntentionTest : RsIntentionTestBase(AddWildcardArmIntention::class) {

    fun `test empty match`() = doUnavailableTest("""
        enum E { A, B, C }
        fn main() {
            let a = E::A;
            match a {
                /*caret*/
            }
        }
    """)

    fun `test empty non-exhaustive match`() = doAvailableTest("""
        fn main() {
            let a = true;
            match a/*caret*/ {
                true => {}
            }
        }
    """, """
        fn main() {
            let a = true;
            match a {
                true => {}
                _ => {}
            }
        }
    """)

    fun `test do not duplicate inspection quick fixes 1`() = doUnavailableTest("""
        enum E { A, B, C }
        fn main() {
            let a = E::A;
            /*caret*/match a {

            }
        }
    """)

    fun `test do not duplicate inspection quick fixes 2`() = doUnavailableTest("""
        enum E { A, B, C }
        fn main() {
            let a = E::A;
            match/*caret*/ a {

            }
        }
    """)

    fun `test do not suggest from nested code`() = doUnavailableTest("""
        enum E { A, B, C }
        fn main() {
            let a = E::A;
            match a {
                E::A => { /*caret*/ }
            }
        }
    """)
}
