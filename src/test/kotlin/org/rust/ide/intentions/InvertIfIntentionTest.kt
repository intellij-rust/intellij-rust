/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.intentions

class InvertIfIntentionTest : RsIntentionTestBase(InvertIfIntention()) {
    fun `test let not available`() = doUnavailableTest(""""
        fn foo(a: Option<i32>) {
            if let Some(x) /*caret*/= a {} else {}
        }
    """)

    fun `test if without condition unavailable`() = doUnavailableTest("""
        fn foo() { if /*caret*/ {} else {}}
    """)

    fun `test if without else branch unavailable`() = doUnavailableTest(""""
        fn foo(a: i32) {
            if a == 10 /*caret*/ {}
        }
    """)

    fun `test if without then branch`() = doUnavailableTest("""
        fn foo(a: i32) {if a == 10/*caret*/ else {}}
    """)

    fun `test simple inversion`() = doAvailableTest("""
        fn foo() {
            if 2 =/*caret*/= 2 {
                Ok(())
            } else {
                Err(())
            }
        }
    """, """
        fn foo() {
            if !(2 == 2) {
                Err(())
            } else {
                Ok(())
            }
        }
    """)

    fun `test simple inversion on one line`() = doAvailableTest("""
        fn foo() {
            if 2 =/*caret*/= 2 { Ok(()) } else { Err(()) }
        }
    """, """
        fn foo() {
            if !(2 == 2) { Err(()) } else { Ok(()) }
        }
    """)

    fun `test bigger condition to simplification`() = doAvailableTest("""
        fn foo() {
            if 2 =/*caret*/= 2 && 3 == 3 { Ok(()) } else { Err(()) }
        }
    """, """
        fn foo() {
            if 2 != 2 || 3 != 3 { Err(()) } else { Ok(()) }
        }
    """)

    fun `test simple inversion strange formatting`() = doAvailableTest("""
        fn foo() {
            if 2 =/*caret*/= 2 {
                Ok(())
            } else { Err(()) }
        }
    """, """
        fn foo() {
            if !(2 == 2) { Err(()) } else {
                Ok(())
            }
        }
    """)

    fun `test very simple condition`() = doAvailableTest("""
        fn foo(cond: bool) {
            if co/*caret*/nd {
                Ok(())
            } else {
                Err(())
            }
        }
    """, """
        fn foo(cond: bool) {
            if !(cond) {
                Err(())
            } else {
                Ok(())
            }
        }
    """)
}
