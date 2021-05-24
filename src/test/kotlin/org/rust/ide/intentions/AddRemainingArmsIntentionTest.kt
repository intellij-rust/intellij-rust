/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.intentions

/**
 * More tests for base functionality can be found in [org.rust.ide.inspections.match.RsNonExhaustiveMatchInspectionTest]
 */
class AddRemainingArmsIntentionTest : RsIntentionTestBase(AddRemainingArmsIntention::class) {

    fun `test empty match`() = doAvailableTest("""
        enum E { A, B, C }
        fn main() {
            let a = E::A;
            match a {
                /*caret*/
            }
        }
    """, """
        enum E { A, B, C }
        fn main() {
            let a = E::A;
            match a {
                E::A => {}
                E::B => {}
                E::C => {}
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
                false => {}
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

    fun `test match with raw identifier-keyword as name and variant`() = doAvailableTest("""
        enum r#type { A, r#if, r#fn }
        fn main() {
            let a = r#type::A;
            match a/*caret*/ {

            }
        }
    """, """
        enum r#type { A, r#if, r#fn }
        fn main() {
            let a = r#type::A;
            match a {
                r#type::A => {}
                r#type::r#if => {}
                r#type::r#fn => {}
            }
        }
    """)
}
