/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.resolve.indexes

import com.intellij.openapi.project.Project
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.stubs.IndexSink
import com.intellij.psi.stubs.StringStubIndexExtension
import com.intellij.psi.stubs.StubIndex
import com.intellij.psi.stubs.StubIndexKey
import org.rust.ide.search.RsWithMacrosProjectScope
import org.rust.lang.core.psi.RsFunction
import org.rust.lang.core.psi.ext.isPublic
import org.rust.lang.core.psi.ext.name
import org.rust.lang.core.psi.ext.returnType
import org.rust.lang.core.psi.ext.valueParameters
import org.rust.lang.core.resolve.knownItems
import org.rust.lang.core.stubs.RsFileStub
import org.rust.lang.core.types.type
import org.rust.openapiext.getElements


data class DeriverableTrait(val name: String, val deriver: RsFunction)

/**
 * Index of functions that can be used to `#[derive]` traits.
 */
class RsDeriveIndex : StringStubIndexExtension<RsFunction>() {
    override fun getVersion(): Int = RsFileStub.Type.stubVersion
    override fun getKey(): StubIndexKey<String, RsFunction> = KEY

    companion object {
        val KEY: StubIndexKey<String, RsFunction> =
            StubIndexKey.createIndexKey("org.rust.lang.core.stubs.index.RustDeriveIndex")

        fun index(fn: RsFunction, sink: IndexSink) {
            fn.potentiallyDeriverableTraitName?.let { traitName ->
                sink.occurrence(KEY, traitName)
            }
        }

        fun allDeriverableTraits(
            project: Project
        ): Collection<DeriverableTrait> =
            StubIndex.getInstance().getAllKeys(KEY, project).flatMap { name ->
                getElements(KEY, name, project, RsWithMacrosProjectScope(project)).map { deriver ->
                    DeriverableTrait(name, deriver)
                }
            }.filter {
                // TODO Can I remove elements from the index if they aren't actually derivers?
                it.deriver.isDeriver
            }

        fun findDeriversByTraitName(
            project: Project,
            target: String,
            scope: GlobalSearchScope = RsWithMacrosProjectScope(project)
        ): Collection<RsFunction> = getElements(KEY, target, project, scope)
            .filter {
                it.isDeriver
            }
    }
}

/**
 * A deriver is a function that can be used to `#[derive]` traits.
 * For a function to be a deriver it must be public, have a
 * `proc_macro_derive` attribute with the name of the trait it can
 * derive specified, a single input parameter of type `proc_macro::TokenStream`
 * and a return type of `proc_macro::TokenStream`.
 * E.g. deriver function:
 * ```rust
 * #[proc_macro_derive(Trait)]
 * pub fn trait_deriver(input: proc_macro::TokenStream) -> proc_macro::TokenStream { /* code */ }
 * ```
 * E.g. invalid functions:
 * ```rust
 * #[proc_macro_derive] // no trait name
 * pub fn trait_deriver(input: proc_macro::TokenStream) -> proc_macro::TokenStream { /* code */ }
 *
 * #[proc_macro_derive(Trait)} // fn must be `pub`
 * fn trait_deriver(input: proc_macro::TokenStream) -> proc_macro::TokenStream { /* code */ }
 * ```
 *
 */
val RsFunction.isDeriver: Boolean
    get() = deriverableTraitName != null

/**
 * Checks if a given function could be a deriver function. Omits checking for return type
 * and parameter type as it's not possible to do so when indexing
 */
private val RsFunction.potentiallyDeriverableTraitName: String?
    get() = this.outerAttrList
        .map { it.metaItem }
        .find { it.name == "proc_macro_derive" }
        ?.metaItemArgs
        ?.metaItemList
        ?.getOrNull(0)
        ?.name
        ?.takeIf {
            this.isPublic && this.valueParameters.size == 1
        }


val RsFunction.deriverableTraitName: String?
    get() = this.outerAttrList
        .map { it.metaItem }
        .find { it.name == "proc_macro_derive" }
        ?.metaItemArgs
        ?.metaItemList
        ?.getOrNull(0)
        ?.name
        ?.takeIf {
            this.isPublic
                && this.returnType == this.knownItems.TokenStream?.declaredType
                && this.valueParameters.size == 1
                && this.valueParameters[0].typeReference?.type == this.knownItems.TokenStream?.declaredType
        }

