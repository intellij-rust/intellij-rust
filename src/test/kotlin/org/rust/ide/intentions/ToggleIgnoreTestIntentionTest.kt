/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.intentions

class ToggleIgnoreTestIntentionTest : RsIntentionTestBase(ToggleIgnoreTestIntention()) {
    fun `test add ignore`() = doAvailableTest("""
        #[test]
        fn foo/*caret*/() {}
    """, """
        #[ignore]
        #[test]
        fn foo() {}
    """)

    fun `test remove ignore`() = doAvailableTest("""
        #[ignore]
        #[test]
        fn foo/*caret*/() {}
    """, """
        #[test]
        fn foo() {}
    """)

    fun `test no ignore`() = doUnavailableTest("""
        fn foo/*caret*/() {}
    """)
}
