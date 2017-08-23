/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.intentions

import com.intellij.testFramework.LightProjectDescriptor

class FillMatchArmsIntentionStdTest : RsIntentionTestBase(FillMatchArmsIntention()) {

    override fun getProjectDescriptor(): LightProjectDescriptor = WithStdlibRustProjectDescriptor

    fun `test Option enum`() = doAvailableTest("""
        fn foo(x: Option<i32>) {
            match x/*caret*/ {}
        }
    """, """
        fn foo(x: Option<i32>) {
            match x {
                None => {/*caret*/},
                Some(_) => {},
            }
        }
    """)

    fun `test Result enum`() = doAvailableTest("""
        fn foo(x: Result<i32, bool>) {
            match x/*caret*/ {}
        }
    """, """
        fn foo(x: Result<i32, bool>) {
            match x {
                Ok(_) => {/*caret*/},
                Err(_) => {},
            }
        }
    """)
}
