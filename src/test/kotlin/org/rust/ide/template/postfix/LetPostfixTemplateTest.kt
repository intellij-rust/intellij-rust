/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.template.postfix

class LetPostfixTemplateTest : PostfixTemplateTest(LetPostfixTemplate()) {
    fun testNotApplicable() = doTestNotApplicable(
        """
        fn foo() {
            println!("test");.let/*caret*/
        }
        """
    )

    fun testSimple() = doTest(
        """
        fn foo() {
            4.let/*caret*/;
        }
        """
        ,
        """
        fn foo() {
            let /*caret*/i = 4;
        }
        """
    )

    fun testSimpleParExpr() = doTest(
        """
        fn foo() {
            (1 + 2).let/*caret*/;
        }
        """
        ,
        """
        fn foo() {
            let /*caret*/x = (1 + 2);
        }
        """
    )

    fun testSimpleFoo() = doTest(
        """
        fn foo() { }

        fn main() {
            foo().let/*caret*/
        }
        """
        ,
        """
        fn foo() { }

        fn main() {
            let /*caret*/foo = foo();
        }
        """
    )

    fun testSimpleFooWithType() = doTest(
        """
        fn foo() -> i32 { 42 }

        fn main() {
            foo().let/*caret*/
        }
        """
        ,
        """
        fn foo() -> i32 { 42 }

        fn main() {
            let /*caret*/i = foo();
        }
        """
    )
}
