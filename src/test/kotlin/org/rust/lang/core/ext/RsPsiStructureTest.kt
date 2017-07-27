/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.ext

import com.intellij.psi.PsiFileFactory
import org.intellij.lang.annotations.Language
import org.rust.lang.RsFileType
import org.rust.lang.RsTestBase
import org.rust.lang.core.psi.RsConstant
import org.rust.lang.core.psi.RsFunction
import org.rust.lang.core.psi.RsImplItem
import org.rust.lang.core.psi.RsTypeAlias
import org.rust.lang.core.psi.ext.*


// Unit tests fo various `parentOfType` like utilities,
// which use `instaceof` under the hood and thus sensitive
// to PSI structure
class RsPsiStructureTest : RsTestBase() {
    private fun checkFunctionOwner(cond: (RsFunctionOwner) -> Boolean, @Language("Rust") code: String) =
        checkElement<RsFunction>(code) { check(cond(it.owner)) }

    fun `test function role free`() = checkFunctionOwner({ it is RsFunctionOwner.Free }, "fn main() {}")
    fun `test function role foreign`() = checkFunctionOwner({ it is RsFunctionOwner.Foreign }, "extern { fn foo(); }")
    fun `test function role trait method`() = checkFunctionOwner({ it is RsFunctionOwner.Trait}, "trait S { fn foo() {} }")
    fun `test function role inherent impl method`() =
        checkFunctionOwner({ it is RsFunctionOwner.Impl && it.isInherentImpl }, "impl S { fn foo() {} }")

    fun `test function role trait impl method`() =
        checkFunctionOwner({ it is RsFunctionOwner.Impl && it.isTraitImpl }, "impl S for T { fn foo() {} }")

    private fun checkTypeAliasOwner(cond: (RsTypeAliasOwner) -> Boolean, @Language("Rust") code: String) =
        checkElement<RsTypeAlias>(code) { check(cond(it.owner)) }

    fun `test type alias role free 1`() = checkTypeAliasOwner({ it is RsTypeAliasOwner.Free }, "type T = ();")
    fun `test type alias role free 2`() = checkTypeAliasOwner({ it is RsTypeAliasOwner.Free }, "fn main() { type T = (); }")
    fun `test type alias role impl method`() = checkTypeAliasOwner({ it is RsTypeAliasOwner.Impl }, "impl S for X { type T = (); }")
    fun `test type alias role trait method`() = checkTypeAliasOwner({ it is RsTypeAliasOwner.Trait }, "trait S { type T; }")

    private fun checkConstantRole(cond: (RsConstantOwner) -> Boolean, @Language("Rust") code: String) =
        checkElement<RsConstant>(code) { check(cond(it.owner)) }

    fun `test constant role free 1`() = checkConstantRole({ it is RsConstantOwner.Free }, "const C: () = ();")
    fun `test constant role free 2`() = checkConstantRole({ it is RsConstantOwner.Free }, "fn main() { const C: () = (); }")
    fun `test constant role foreign`() = checkConstantRole({ it is RsConstantOwner.Foreign }, "extern { static C: (); }")
    fun `test constant role impl method`() = checkConstantRole({ it is RsConstantOwner.Impl }, "impl S for X { const C: () = (); }")
    fun `test constant role trait method`() = checkConstantRole({ it is RsConstantOwner.Trait }, "trait S { const C: () = (); }")

    fun `test trait implementation info`() = checkElement<RsImplItem>("""
        trait T {
            fn optional_fn() { }
            fn required_fn();
            fn another_required_fn();
            type RequiredType;
        }

        struct S;

        impl T for S {
            fn another_required_fn() { }
            fn spam() {}
        }
    """) { impl ->
        val trait = impl.traitRef!!.resolveToTrait!!
        val implInfo = TraitImplementationInfo.create(trait, impl)!!
        check(implInfo.declared.map { it.name } == listOf(
            "optional_fn", "required_fn", "another_required_fn", "RequiredType"
        ))

        check(implInfo.missingImplementations.map { it.name } == listOf(
            "required_fn", "RequiredType"
        ))

        check(implInfo.nonExistentInTrait.map { it.name } == listOf(
            "spam"
        ))

        check(implInfo.implementationToDeclaration.map { (it.first.name to it.second.name) } == listOf(
            "another_required_fn" to "another_required_fn"
        ))
    }


    private inline fun <reified E : RsCompositeElement> checkElement(@Language("Rust") code: String, callback: (E) -> Unit) {
        val element = PsiFileFactory.getInstance(project)
            .createFileFromText("main.rs", RsFileType, code)
            .childOfType<E>() ?: error("No ${E::class.java} in\n$code")

        callback(element)
    }

}
