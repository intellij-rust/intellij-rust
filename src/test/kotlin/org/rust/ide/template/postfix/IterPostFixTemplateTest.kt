/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.template.postfix

import org.rust.ProjectDescriptor
import org.rust.WithStdlibRustProjectDescriptor

@ProjectDescriptor(WithStdlibRustProjectDescriptor::class)
abstract class IterPostFixTemplateTestBase(private val key: String) :
    RsPostfixTemplateTest(IterPostfixTemplate(key, RsPostfixTemplateProvider())) {

    fun `test non iterable expr`() = doTestNotApplicable("""
            let b = 5;
            b./*caret*/
        """
    )

    fun `test iterable expr`() = doTest("""
        fn main(){
            let v = vec![1, 2, 3];
            v.iter().$key/*caret*/
        }
    """, """
        fn main(){
            let v = vec![1, 2, 3];
            for x in v.iter() {
                /*caret*/
            }
        }
    """)

    fun `test intoIterable expr`() = doTest("""
        fn main(){
            let v = vec![1, 2, 3];
            v.$key/*caret*/
        }
    """, """
        fn main(){
            let v = vec![1, 2, 3];
            for x in v {
                /*caret*/
            }
        }
    """)
}

class IterPostFixTemplateTest : IterPostFixTemplateTestBase("iter")
class ForPostFixTemplateTest : IterPostFixTemplateTestBase("for")
