/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.annotator.fixes

import org.rust.ide.annotator.RsAnnotatorTestBase
import org.rust.ide.annotator.RsErrorAnnotator

class CreateLifetimeParameterFromUsageFixTest : RsAnnotatorTestBase(RsErrorAnnotator::class) {

    fun `test fix when empty parameters`() = checkFixByText("Create lifetime parameter", """
        struct Foo<> {
            x: &<error descr="Use of undeclared lifetime name `'a` [E0261]">'a/*caret*/</error> x
        }
    """, """
        struct Foo<'a> {
            x: &'a x
        }
    """)

    fun `test fix when non empty parameters`() = checkFixByText("Create lifetime parameter", """
        struct Foo<'b, 'c, T> {
            x: &<error descr="Use of undeclared lifetime name `'a` [E0261]">'a/*caret*/</error> x
        }
    """, """
        struct Foo<'b, 'c, 'a, T> {
            x: &'a x
        }
    """)

    fun `test fix when no parameters`() = checkFixByText("Create lifetime parameter", """
        struct Foo {
            x: &<error descr="Use of undeclared lifetime name `'a` [E0261]">'a/*caret*/</error> x
        }
    """, """
        struct Foo<'a> {
            x: &'a x
        }
    """)

    fun `test folded`() = checkFixByText("Create lifetime parameter", """
        trait Tr {
            fn foo() {
                struct S {
                    r: &<error descr="Use of undeclared lifetime name `'a` [E0261]">'a/*caret*/</error> r
                }
            }
        }
    """, """
        trait Tr {
            fn foo() {
                struct S<'a> {
                    r: &'a r
                }
            }
        }
    """)
}
