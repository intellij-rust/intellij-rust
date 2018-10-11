/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.type

import org.intellij.lang.annotations.Language
import org.rust.ProjectDescriptor
import org.rust.RsTestBase
import org.rust.WithStdlibRustProjectDescriptor
import org.rust.lang.core.psi.RsTypeReference
import org.rust.lang.core.types.ty.TyTraitObject
import org.rust.lang.core.types.ty.walk
import org.rust.lang.core.types.type

class RsTyTraitObjectRegionTest : RsTestBase() {
    fun `test trait under ref`() = doTest("""
        trait Trait {}
        fn foo<'a>(x: &'a Trait) {}
                    //^ 'a
    """)

    fun `test trait under refs`() = doTest("""
        trait Trait {}
        fn foo<'a, 'b>(x: &'a &'b Trait) {}
                        //^ 'b
    """)

    fun `test 'static region bound in struct`() = doTest("""
        trait Trait {}
        struct Struct<T: 'static> {}
        fn foo<'a>(x: Struct<Trait>) {}
                    //^ 'static
    """)

    fun `test no region bound it struct`() = doTest("""
        trait Trait {}
        struct Struct<'a, T> {}
        fn foo<'b>(x: Struct<'b, Trait>) {}
                    //^ 'static
    """)

    fun `test region bound it struct`() = doTest("""
        trait Trait {}
        struct Struct<'a, T: 'a> {}
        fn foo<'b>(x: Struct<'b, Trait>) {}
                    //^ 'b
    """)

    fun `test ambiguity region bound it struct`() = doTest("""
        trait Trait {}
        struct Struct<'a, 'b, T: 'a + 'b> {}
        fn foo<'c, 'd>(x: Struct<'c, 'd, Trait>) {}
                        //^ '_
    """)

    @ProjectDescriptor(WithStdlibRustProjectDescriptor::class)
    fun `test box`() = doTest("""
        trait Trait {}
        fn foo<'a>(x: Box<Trait>) {}
                    //^ 'static
    """)

    @ProjectDescriptor(WithStdlibRustProjectDescriptor::class)
    fun `test box under ref`() = doTest("""
        trait Trait {}
        fn foo<'a>(x: &'a Box<Trait>) {}
                    //^ 'a
    """)

    @ProjectDescriptor(WithStdlibRustProjectDescriptor::class)
    fun `test explicit 'static overrides ref's region`() = doTest("""
        trait Trait {}
        fn foo<'a>(x: &'a Box<Trait + 'static>) {}
                    //^ 'static
    """)

    /** Checks the region of the trait object in [code] pointed to by `//^` marker. */
    private fun doTest(@Language("Rust") code: String) {
        InlineFile(code)
        val (typeAtCaret, expectedRegion) = findElementAndDataInEditor<RsTypeReference>()
        val ty = typeAtCaret.type
        val traitObjectTy = ty.walk().asSequence().filterIsInstance<TyTraitObject>().first()
        val actualRegion = traitObjectTy.region.toString()
        check(actualRegion == expectedRegion) {
            "$actualRegion != $expectedRegion"
        }
    }
}
