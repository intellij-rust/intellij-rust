/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.annotator

class RsOnlyAutoTraitsInTraitObjectTest : RsAnnotatorTestBase(RsErrorAnnotator::class) {

    fun `test E0225 error when bound to more than one non-auto trait`() = checkByText("""
        trait A {}
        trait B {}

        type Foo = Box<dyn A + /*error descr="Only auto traits can be used as additional traits in a trait object [E0225]"*/B/*error**/>;
    """)

    fun `test E0225 no error when bound to one non-auto trait`() = checkByText("""
        trait A {}
        auto trait AutoTrait {}

        type Foo = Box<dyn A + AutoTrait>;
    """)

    fun `test E0225 no error when one trait is unresolved`() = checkByText("""
        trait A {}

        type Foo = Box<dyn A + Unresolved>;
    """)

    fun `test E0225 no error when bound to only one trait`() = checkByText("""
        trait A {}

        type Foo = Box<dyn A>;
    """)
}
