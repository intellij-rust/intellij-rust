/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.annotator

class RsUnsafeInherentImplTest: RsAnnotatorTestBase(RsSyntaxErrorsAnnotator::class) {
    fun `test E0197`() = checkByText("""
        struct Foo;
        unsafe impl <error descr="Inherent impls cannot be unsafe [E0197]">Foo</error> { }
    """)

    fun `test E0197 unsafe with trait`() = checkByText("""
        struct Foo;
        unsafe trait Bar { }
        unsafe impl Kek for Foo { }
    """)
}
