/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.template.postfix

class NotPostfixTemplateTest : PostfixTemplateTest(NotPostfixTemplate()) {
    fun `test simple`() = doTest("""
        fn foo() {
            assert!(nodes.is_empty().not/*caret*/);
        }
    """, """
        fn foo() {
            assert!(!nodes.is_empty()/*caret*/);
        }
    """)
}
