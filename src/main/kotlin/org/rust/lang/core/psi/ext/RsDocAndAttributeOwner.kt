/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.psi.ext

import com.intellij.extapi.psi.StubBasedPsiElementBase
import com.intellij.openapi.util.registry.Registry
import com.intellij.psi.NavigatablePsiElement
import com.intellij.psi.PsiElement
import org.rust.lang.core.psi.*
import org.rust.lang.core.stubs.RsAttributeOwnerStub
import org.rust.lang.utils.evaluation.CfgEvaluator
import org.rust.lang.utils.evaluation.ThreeValuedLogic

interface RsDocAndAttributeOwner : RsElement, NavigatablePsiElement

interface RsInnerAttributeOwner : RsDocAndAttributeOwner {
    /**
     * Outer attributes are always children of the owning node.
     * In contrast, inner attributes can be either direct
     * children or grandchildren.
     */
    val innerAttrList: List<RsInnerAttr>
}

/**
 * An element with attached outer attributes and documentation comments.
 * Such elements should use left edge binder to properly wrap preceding comments.
 *
 * Fun fact: in Rust, documentation comments are a syntactic sugar for attribute syntax.
 *
 * ```
 * /// docs
 * fn foo() {}
 * ```
 *
 * is equivalent to
 *
 * ```
 * #[doc="docs"]
 * fn foo() {}
 * ```
 */
interface RsOuterAttributeOwner : RsDocAndAttributeOwner {
    @JvmDefault
    val outerAttrList: List<RsOuterAttr>
        get() = stubChildrenOfType()
}

/**
 * Find the first outer attribute with the given identifier.
 */
fun RsOuterAttributeOwner.findOuterAttr(name: String): RsOuterAttr? =
    outerAttrList.find { it.metaItem.name == name }


fun RsOuterAttributeOwner.addOuterAttribute(attribute: Attribute, anchor: PsiElement): RsOuterAttr {
    val attr = RsPsiFactory(project).createOuterAttr(attribute.text)
    return addBefore(attr, anchor) as RsOuterAttr
}

fun RsInnerAttributeOwner.addInnerAttribute(attribute: Attribute, anchor: PsiElement): RsInnerAttr {
    val attr = RsPsiFactory(project).createInnerAttr(attribute.text)
    return addBefore(attr, anchor) as RsInnerAttr
}

data class Attribute(val name: String, val argText: String? = null) {
    val text: String get() = if (argText == null) name else "$name($argText)"
}

inline val RsDocAndAttributeOwner.attributeStub: RsAttributeOwnerStub?
    get() = (this as? StubBasedPsiElementBase<*>)?.greenStub as? RsAttributeOwnerStub

/**
 * Returns [QueryAttributes] for given PSI element.
 */
val RsDocAndAttributeOwner.queryAttributes: QueryAttributes
    get() {
        val stub = attributeStub
        return if (stub?.hasAttrs == false) {
            QueryAttributes.EMPTY
        } else {
            val attributes: Sequence<RsAttr> = (this as? RsInnerAttributeOwner)?.innerAttrList.orEmpty().asSequence() +
                (this as? RsOuterAttributeOwner)?.outerAttrList.orEmpty().asSequence()
            QueryAttributes(attributes)
        }
    }

/**
 * Allows for easy querying [RsDocAndAttributeOwner] for specific attributes.
 *
 * **Do not instantiate directly**, use [RsDocAndAttributeOwner.queryAttributes] instead.
 */
class QueryAttributes(
    private val attributes: Sequence<RsAttr>
) {
    // #[doc(hidden)]
    val isDocHidden: Boolean get() = hasAttributeWithArg("doc", "hidden")

    // #[cfg(test)], #[cfg(target_has_atomic = "ptr")], #[cfg(all(target_arch = "wasm32", not(target_os = "emscripten")))]
    fun hasCfgAttr(): Boolean =
        hasAttribute("cfg")

    // `#[attributeName]`, `#[attributeName(arg)]`, `#[attributeName = "Xxx"]`
    fun hasAttribute(attributeName: String): Boolean {
        val attrs = attrsByName(attributeName)
        return attrs.any()
    }

    fun hasAttribute(regex: Regex): Boolean {
        val attrs = attrsByText(regex)
        return attrs.any()
    }

    fun hasAnyOfAttributes(vararg names: String): Boolean =
        attributes.any { it.metaItem.name in names }

    // `#[attributeName]`
    fun hasAtomAttribute(attributeName: String): Boolean {
        val attrs = attrsByName(attributeName)
        return attrs.any { !it.hasEq && it.metaItemArgs == null }
    }

    // `#[attributeName(arg)]`
    fun hasAttributeWithArg(attributeName: String, arg: String): Boolean {
        val attrs = attrsByName(attributeName)
        return attrs.any { it.metaItemArgs?.metaItemList?.any { it.name == arg } ?: false }
    }

    // `#[attributeName(arg)]`
    fun getFirstArgOfSingularAttribute(attributeName: String): String? {
        return attrsByName(attributeName).singleOrNull()
            ?.metaItemArgs?.metaItemList?.firstOrNull()
            ?.name
    }

    // `#[attributeName(key = "value")]`
    fun hasAttributeWithKeyValue(attributeName: String, key: String, value: String): Boolean {
        val attrs = attrsByName(attributeName)
        return attrs.any {
            it.metaItemArgs?.metaItemList?.any { it.name == key && it.value == value } ?: false
        }
    }

    fun lookupStringValueForKey(key: String): String? =
        metaItems
            .filter { it.name == key }
            .mapNotNull { it.value }
            .singleOrNull()

    // #[lang = "copy"]
    val langAttribute: String?
        get() = getStringAttributes("lang").firstOrNull()

    // #[derive(Clone)], #[derive(Copy, Clone, Debug)]
    val deriveAttributes: Sequence<RsMetaItem>
        get() = attrsByName("derive")

    // #[repr(u16)], #[repr(C, packed)], #[repr(simd)], #[repr(align(8))]
    val reprAttributes: Sequence<RsMetaItem>
        get() = attrsByName("repr")

    // #[deprecated(since, note)], #[rustc_deprecated(since, reason)]
    val deprecatedAttribute: RsMetaItem?
        get() = (attrsByName("deprecated") + attrsByName("rustc_deprecated")).firstOrNull()

    val cfgAttributes: Sequence<RsMetaItem>
        get() = attrsByName("cfg")

    val unstableAttributes: Sequence<RsMetaItem>
        get() = attrsByName("unstable")

    // `#[attributeName = "Xxx"]`
    private fun getStringAttributes(attributeName: String): Sequence<String?> =
        attrsByName(attributeName).map { it.value }

    val metaItems: Sequence<RsMetaItem>
        get() = attributes.mapNotNull { it.metaItem }

    /**
     * Get a sequence of all attributes named [name]
     */
    private fun attrsByName(name: String): Sequence<RsMetaItem> = metaItems.filter { it.name == name }

    /**
     * Get a sequence of all attributes that match the given [regex]
     */
    private fun attrsByText(regex: Regex): Sequence<RsMetaItem> = metaItems.filter {
        val text = it.text ?: return@filter false
        text.matches(regex)
    }

    override fun toString(): String =
        "QueryAttributes(${attributes.joinToString { it.text }})"

    companion object {
        val EMPTY: QueryAttributes = QueryAttributes(emptySequence())
    }
}

/**
 * Checks if there are any #[cfg()] attributes that disable this element
 *
 * HACK: do not check on [RsFile] as [RsFile.queryAttributes] would access the PSI
 */
val RsDocAndAttributeOwner.isEnabledByCfgSelf: Boolean
    get() = evaluateCfg() != ThreeValuedLogic.False

val RsDocAndAttributeOwner.isCfgUnknownSelf: Boolean
    get() = evaluateCfg() == ThreeValuedLogic.Unknown

private fun RsDocAndAttributeOwner.evaluateCfg(): ThreeValuedLogic {
    if (!CFG_ATTRIBUTES_ENABLED_KEY.asBoolean()) return ThreeValuedLogic.True

    // TODO: add cfg to RsFile's stub and remove this line
    if (this is RsFile) return ThreeValuedLogic.True

    if (attributeStub?.hasCfg == false) return ThreeValuedLogic.True

    val cfgAttributes = queryAttributes.cfgAttributes

    // TODO: When we open both cargo projects for an application and a library,
    // this will return the library as containing package.
    // When the application now requests certain features, which are not enabled by default in the library
    // we will evaluate features wrongly here
    val crate = containingCrate ?: return ThreeValuedLogic.True // TODO: maybe unknown?
    val features = crate.featureState
    return CfgEvaluator(crate.cargoWorkspace.cfgOptions, crate.cfgOptions, features, crate.origin).evaluate(cfgAttributes)
}

private val CFG_ATTRIBUTES_ENABLED_KEY = Registry.get("org.rust.lang.cfg.attributes")
