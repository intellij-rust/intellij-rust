/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.annotator

class RsTypeParamsInExternAnnotatorTest : RsAnnotatorTestBase(RsSyntaxErrorsAnnotator::class) {
    fun `test E0044 type param`() = checkByText("""
        extern "C" {
            fn foo<error descr="Foreign items may not have type parameters [E0044]"><T></error>();
        }
    """)

    fun `test E0044 const type param`() = checkByText("""
        extern "C" {
            fn foo<error descr="Foreign items may not have const parameters [E0044]"><const X: usize></error>();
        }
    """)

    fun `test E0044 type and const type params`() = checkByText("""
        extern "C" {
            fn foo<error descr="Foreign items may not have type or const parameters [E0044]"><T, const X: usize></error>();
        }
    """)

    fun `test E0044 allow lifetime param`() = checkByText("""
        extern "C" {
            fn foo<'a>();
        }
    """)
}
