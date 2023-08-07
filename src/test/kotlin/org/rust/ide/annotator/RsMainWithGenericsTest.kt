/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.annotator

class RsMainWithGenericsTest: RsAnnotatorTestBase(RsSyntaxErrorsAnnotator::class) {
    fun `test E0131 type parameter`() = checkByText("""
        fn main<error descr="`main` function is not allowed to have generic parameters [E0131]"><T></error>() { }
    """)

    fun `test E0131 lifetime parameter`() = checkByText("""
        fn main<error descr="`main` function is not allowed to have generic parameters [E0131]"><'a></error>() { }
    """)


    fun `test E0131 const parameter`() = checkByText("""
        fn main<error descr="`main` function is not allowed to have generic parameters [E0131]"><const A: i32></error>() { }
    """)

    fun `test E0131 without type parameters`() = checkByText("""
        fn main() { }
    """)
}
