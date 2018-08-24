/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.type

import org.rust.ProjectDescriptor
import org.rust.RsTestBase
import org.rust.WithStdlibRustProjectDescriptor
import org.rust.lang.core.psi.RsTraitItem
import org.rust.lang.core.psi.RsTypeParamBounds
import org.rust.lang.core.psi.ext.withSubst
import org.rust.lang.core.resolve.ImplLookup
import org.rust.lang.core.types.TraitRef
import org.rust.lang.core.types.ty.*

@ProjectDescriptor(WithStdlibRustProjectDescriptor::class)
class RsPrimitiveTypeImplsTest : RsTestBase() {
    fun `test Sized types`() = doTest(TyInteger.VALUES + TyFloat.VALUES + TyBool + TyChar + TyUnit, "Sized")
    fun `test Clone types`() = doTest(TyInteger.VALUES + TyFloat.VALUES + TyBool + TyChar + TyUnit, "Clone")
    fun `test Copy types`() = doTest(TyInteger.VALUES + TyFloat.VALUES + TyBool + TyChar + TyUnit, "Copy")
    fun `test Default types`() = doTest(TyInteger.VALUES + TyFloat.VALUES + TyBool + TyChar + TyUnit, "Default")
    fun `test Debug types`() = doTest(TyInteger.VALUES + TyFloat.VALUES + TyBool + TyChar + TyUnit + TyStr, "std::fmt::Debug")
    fun `test PartialEq types`() = doTest(TyInteger.VALUES + TyFloat.VALUES + TyBool + TyChar + TyUnit + TyStr, "PartialEq")
    fun `test Eq types`() = doTest(TyInteger.VALUES + TyBool + TyChar + TyUnit + TyStr, "Eq")
    fun `test PartialOrd types`() = doTest(TyInteger.VALUES + TyFloat.VALUES + TyBool + TyChar + TyUnit + TyStr, "PartialOrd")
    fun `test Ord types`() = doTest(TyInteger.VALUES + TyBool + TyChar + TyUnit + TyStr, "Ord")
    fun `test Hash types`() = doTest(TyInteger.VALUES + TyBool + TyChar + TyStr, "std::hash::Hash")

    private fun doTest(primitiveTypes: List<TyPrimitive>, trait: String) {
        InlineFile("""
            fn foo<T: $trait>(x: T) {}
                    //^
        """)
        val typeBounds = findElementInEditor<RsTypeParamBounds>()
        val traitItems = typeBounds.polyboundList.map {
            it.bound.traitRef?.path?.reference?.resolve() as? RsTraitItem ?: error("Can't find type bounds")
        }

        val lookup = ImplLookup.relativeTo(typeBounds)
        for (type in primitiveTypes) {
            for (traitItem in traitItems) {
                check(lookup.canSelect(TraitRef(type, traitItem.withSubst(type)))) {
                    "`$type` should implement `${traitItem.name}` trait"
                }
            }
        }
    }
}
