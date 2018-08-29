/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.template.postfix

import com.intellij.testFramework.LightProjectDescriptor
import org.rust.WithStdlibRustProjectDescriptor

class IterPostFixTemplateTest : PostfixTemplateTest(IterPostfixTemplate(RsPostfixTemplateProvider())) {
    override fun getProjectDescriptor(): LightProjectDescriptor = WithStdlibRustProjectDescriptor

    fun testNonIterable() = doTestNotApplicable(
        """
            let b = 5;
            b./*caret*/
        """
    )
    fun testIntoIterable() = doTest("""
        fn main(){
            let v = vec![1, 2, 3];
            v.iter/*caret*/
        }
    """,
        """
        fn main(){
            let v = vec![1, 2, 3];
            for x in v.into_iter() {
                /*caret*/
            }
        }
    """)
    fun testSimple() = doTest(
        """
    fn main(){
            let mut array: [i32; 3] = [0; 3];
            let b = array.iter();
            b.iter/*caret*/
    }
        """,
        """
    fn main(){
            let mut array: [i32; 3] = [0; 3];
            let b = array.iter();
        for x in b {
            /*caret*/
        }
    }
        """
    )
}
