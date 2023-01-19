/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.template.postfix

class RefTypePostfixTemplateTest : RsPostfixTemplateTest(RefTypePostfixTemplate::class) {
    fun `test type reference`() = doTest("""
        fn foo(a: str.ref/*caret*/) {}
    """, """
        fn foo(a: &str/*caret*/) {}
    """)
}
