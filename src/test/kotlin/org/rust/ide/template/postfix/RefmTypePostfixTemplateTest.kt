/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.template.postfix

class RefmTypePostfixTemplateTest : RsPostfixTemplateTest(RefmTypePostfixTemplate::class) {
    fun `test type reference`() = doTest("""
        fn foo(a: str.refm/*caret*/) {}
    """, """
        fn foo(a: &mut str/*caret*/) {}
    """)
}
