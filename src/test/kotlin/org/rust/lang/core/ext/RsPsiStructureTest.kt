/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.ext

import com.intellij.psi.PsiFileFactory
import org.intellij.lang.annotations.Language
import org.rust.RsTestBase
import org.rust.ide.presentation.presentationInfo
import org.rust.lang.RsFileType
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.*


// Unit tests for various `ancestorStrict` like utilities,
// which use `instanceof` under the hood and thus sensitive
// to PSI structure
class RsPsiStructureTest : RsTestBase() {
    private fun checkFunctionOwner(cond: (RsAbstractableOwner) -> Boolean, @Language("Rust") code: String) =
        checkElement<RsFunction>(code) { check(cond(it.owner)) }

    fun `test function role free`() = checkFunctionOwner({ it is RsAbstractableOwner.Free }, "fn main() {}")
    fun `test function role foreign`() = checkFunctionOwner({ it is RsAbstractableOwner.Foreign }, "extern { fn foo(); }")
    fun `test function role trait method`() = checkFunctionOwner({ it is RsAbstractableOwner.Trait }, "trait S { fn foo() {} }")
    fun `test function role inherent impl method`() =
        checkFunctionOwner({ it is RsAbstractableOwner.Impl && it.isInherentImpl }, "impl S { fn foo() {} }")

    fun `test function role trait impl method`() =
        checkFunctionOwner({ it is RsAbstractableOwner.Impl && it.isTraitImpl }, "impl S for T { fn foo() {} }")

    private fun checkTypeAliasOwner(cond: (RsAbstractableOwner) -> Boolean, @Language("Rust") code: String) =
        checkElement<RsTypeAlias>(code) { check(cond(it.owner)) }

    fun `test type alias role free 1`() = checkTypeAliasOwner({ it is RsAbstractableOwner.Free }, "type T = ();")
    fun `test type alias role free 2`() = checkTypeAliasOwner({ it is RsAbstractableOwner.Free }, "fn main() { type T = (); }")
    fun `test type alias role impl method`() = checkTypeAliasOwner({ it is RsAbstractableOwner.Impl }, "impl S for X { type T = (); }")
    fun `test type alias role trait method`() = checkTypeAliasOwner({ it is RsAbstractableOwner.Trait }, "trait S { type T; }")

    private fun checkConstantRole(cond: (RsAbstractableOwner) -> Boolean, @Language("Rust") code: String) =
        checkElement<RsConstant>(code) { check(cond(it.owner)) }

    fun `test constant role free 1`() = checkConstantRole({ it is RsAbstractableOwner.Free }, "const C: () = ();")
    fun `test constant role free 2`() = checkConstantRole({ it is RsAbstractableOwner.Free }, "fn main() { const C: () = (); }")
    fun `test constant role foreign`() = checkConstantRole({ it is RsAbstractableOwner.Foreign }, "extern { static C: (); }")
    fun `test constant role impl method`() = checkConstantRole({ it is RsAbstractableOwner.Impl }, "impl S for X { const C: () = (); }")
    fun `test constant role trait method`() = checkConstantRole({ it is RsAbstractableOwner.Trait }, "trait S { const C: () = (); }")

    fun `test trait implementation info`() = checkElement<RsImplItem>("""
        trait T {
            fn optional_fn() { }
            fn required_fn();
            fn another_required_fn();
            type RequiredType;
            type same_name;
            const same_name: i32;
        }

        struct S;

        impl T for S {
            fn another_required_fn() { }
            type same_name = ();
        }
    """) { impl ->
        val trait = impl.traitRef!!.resolveToTrait()!!
        val implInfo = TraitImplementationInfo.create(trait, impl)!!
        check(implInfo.declared.map { it.name } == listOf(
            "optional_fn", "required_fn", "another_required_fn", "RequiredType", "same_name", "same_name"
        ))

        check(implInfo.missingImplementations.map { it.name } == listOf(
            "required_fn", "RequiredType", "same_name"
        ))
    }

    fun `test extern crate presentation info`() = checkElement<RsExternCrateItem>("""
        #[macro_use]
        #[macro_reexport(vec, format)]
        extern crate collections as core_collections;
    """) {
        val info = it.presentationInfo!!.signatureText
        check(info == "extern crate <b>collections</b>")
    }

    private inline fun <reified E : RsElement> checkElement(@Language("Rust") code: String, callback: (E) -> Unit) {
        val element = PsiFileFactory.getInstance(project)
            .createFileFromText("main.rs", RsFileType, code)
            .descendantOfTypeStrict<E>() ?: error("No ${E::class.java} in\n$code")

        callback(element)
    }

}
