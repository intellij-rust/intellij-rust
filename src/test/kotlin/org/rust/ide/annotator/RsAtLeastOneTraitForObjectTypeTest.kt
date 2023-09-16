/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.annotator

class RsAtLeastOneTraitForObjectTypeTest : RsAnnotatorTestBase(RsSyntaxErrorsAnnotator::class) {
    fun `test E0224 lifetime without any traits`() = checkByText("""
        type Foo = <error descr="At least one trait is required for an object type [E0224]">dyn 'static</error>;
    """)
    fun `test E0224 lifetime with one trait`() = checkByText("""
        type Foo = dyn 'static + Copy;
    """)

    fun `test E0224 lifetime with many traits`() = checkByText("""
        type Foo = dyn 'static + Copy + Send;
    """)

    fun `test E0224 only trait`() = checkByText("""
        type Foo = Copy;
    """)
}
