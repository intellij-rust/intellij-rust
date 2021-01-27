/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.psi.ext

import com.intellij.extapi.psi.StubBasedPsiElementBase
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.util.registry.Registry
import com.intellij.psi.NavigatablePsiElement
import com.intellij.psi.PsiElement
import com.intellij.psi.StubBasedPsiElement
import com.intellij.psi.stubs.StubElement
import org.rust.lang.core.crate.Crate
import org.rust.lang.core.psi.*
import org.rust.lang.core.stubs.RsAttributeOwnerStub
import org.rust.lang.utils.evaluation.CfgEvaluator
import org.rust.lang.utils.evaluation.ThreeValuedLogic
import org.rust.openapiext.testAssert
import org.rust.stdext.nextOrNull

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
    get() = when (this) {
        is StubBasedPsiElementBase<*> -> greenStub
        is RsFile -> greenStub
        else -> null
    } as? RsAttributeOwnerStub

/**
 * Returns [QueryAttributes] for given PSI element after `#[cfg_attr()]` expansion
 */
val RsDocAndAttributeOwner.queryAttributes: QueryAttributes
    get() = getQueryAttributes(null)

/** [explicitCrate] is passed to avoid resolve triggering */
fun RsDocAndAttributeOwner.getQueryAttributes(
    explicitCrate: Crate?,
    stub: RsAttributeOwnerStub? = attributeStub
): QueryAttributes {
    testAssert { !DumbService.isDumb(project) }
    return when {
        // No attributes - return empty sequence
        stub?.hasAttrs == false -> QueryAttributes.EMPTY

        // No `#[cfg_attr()]` attributes - return attributes without `cfg_attr` expansion
        stub?.hasCfgAttr == false -> rawAttributes

        // Slow path. There are `#[cfg_attr()]` attributes - expand them and return expanded attributes
        else -> getExpandedAttributesNoStub(explicitCrate)
    }
}

/** [explicitCrate] is passed to avoid resolve triggering */
private fun RsDocAndAttributeOwner.getExpandedAttributesNoStub(explicitCrate: Crate?): QueryAttributes {
    if (!CFG_ATTRIBUTES_ENABLED_KEY.asBoolean()) return rawAttributes
    val crate = explicitCrate ?: containingCrate ?: return rawAttributes
    val evaluator = CfgEvaluator.forCrate(crate)
    return QueryAttributes(evaluator.expandCfgAttrs(rawMetaItems))
}

fun CfgEvaluator.expandCfgAttrs(rawMetaItems: Sequence<RsMetaItem>): Sequence<RsMetaItem> {
    return rawMetaItems.flatMap {
        if (it.name == "cfg_attr") {
            val list = it.metaItemArgs?.metaItemList?.iterator() ?: return@flatMap emptySequence()
            val condition = list.nextOrNull() ?: return@flatMap emptySequence()
            if (evaluateCondition(condition) != ThreeValuedLogic.False) {
                expandCfgAttrs(list.asSequence())
            } else {
                emptySequence()
            }
        } else {
            sequenceOf(it)
        }
    }
}

fun RsDocAndAttributeOwner.getTraversedRawAttributes(withCfgAttrAttribute: Boolean = false): QueryAttributes {
    return QueryAttributes(rawMetaItems.withFlattenCfgAttrsAttributes(withCfgAttrAttribute))
}

fun Sequence<RsMetaItem>.withFlattenCfgAttrsAttributes(withCfgAttrAttribute: Boolean): Sequence<RsMetaItem> =
    flatMap {
        if (it.name == "cfg_attr") {
            val metaItems = it.metaItemArgs?.metaItemList ?: return@flatMap emptySequence()
            val nested = metaItems.asSequence().drop(1)
            if (withCfgAttrAttribute) {
                sequenceOf(it) + nested
            } else {
                nested
            }
        } else {
            sequenceOf(it)
        }
    }

private val RsDocAndAttributeOwner.rawAttributes: QueryAttributes
    get() = QueryAttributes(rawMetaItems)

private val RsDocAndAttributeOwner.rawMetaItems: Sequence<RsMetaItem>
    get() {
        val attributes: Sequence<RsAttr> = (this as? RsInnerAttributeOwner)?.innerAttrList.orEmpty().asSequence() +
            (this as? RsOuterAttributeOwner)?.outerAttrList.orEmpty().asSequence()
        return attributes.map { it.metaItem }
    }

class StubbedAttributeProperty<P, S>(
    private val fromQuery: (QueryAttributes) -> Boolean,
    private val mayHave: (S) -> Boolean
) where P : RsDocAndAttributeOwner,
        P : StubBasedPsiElement<out S>,
        S : StubElement<P>,
        S : RsAttributeOwnerStub
{
    fun getByStub(stub: S, crate: Crate?): Boolean {
        if (!stub.hasAttrs) return false

        return if (!mayHave(stub)) {
            // The attribute name doesn't noticed in root attributes (including nested `#[cfg_attr()]`)
            false
        } else {
            // There is such attribute name in root attributes (maybe under `#[cfg_attr()]`)
            if (!stub.hasCfgAttr) {
                // No `#[cfg_attr()]` attributes
                true
            } else {
                // Slow path. There are `#[cfg_attr()]` attributes, so we should expand them
                fromQuery(stub.psi.getExpandedAttributesNoStub(crate))
            }
        }
    }

    fun getByPsi(psi: P): Boolean {
        val stub = psi.greenStub
        return if (stub !== null) {
            getByStub(stub, null)
        } else {
            fromQuery(psi.getExpandedAttributesNoStub(null))
        }
    }

    fun getDuringIndexing(psi: P): Boolean =
        fromQuery(psi.getTraversedRawAttributes())
}

/**
 * Allows for easy querying [RsDocAndAttributeOwner] for specific attributes.
 *
 * **Do not instantiate directly**, use [RsDocAndAttributeOwner.queryAttributes] instead.
 */
class QueryAttributes(
    val metaItems: Sequence<RsMetaItem>
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

    fun hasAnyOfAttributes(vararg names: String): Boolean =
        metaItems.any { it.name in names }

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

    // #[lang = "copy"]
    val langAttributes: Sequence<String>
        get() = getStringAttributes("lang")

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
    private fun getStringAttributes(attributeName: String): Sequence<String> =
        attrsByName(attributeName).mapNotNull { it.value }

    /**
     * Get a sequence of all attributes named [name]
     */
    fun attrsByName(name: String): Sequence<RsMetaItem> = metaItems.filter { it.name == name }

    override fun toString(): String =
        "QueryAttributes(${metaItems.joinToString { it.text }})"

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

/** [crate] is passed to avoid trigger resolve */
fun RsAttributeOwnerStub.isEnabledByCfgSelf(crate: Crate): Boolean {
    if (!mayHaveCfg) return true
    // TODO: Don't use psi
    val psi = (this as StubElement<*>).psi as RsDocAndAttributeOwner
    return psi.evaluateCfg(crate) != ThreeValuedLogic.False
}

/** [crateOrNull] is passed to avoid trigger resolve */
private fun RsDocAndAttributeOwner.evaluateCfg(crateOrNull: Crate? = null): ThreeValuedLogic {
    if (!CFG_ATTRIBUTES_ENABLED_KEY.asBoolean()) return ThreeValuedLogic.True

    // TODO: add cfg to RsFile's stub and remove this line
    if (this is RsFile) return ThreeValuedLogic.True

    if (attributeStub?.mayHaveCfg == false) return ThreeValuedLogic.True

    val crate = crateOrNull ?: containingCrate ?: return ThreeValuedLogic.True // TODO: maybe unknown?
    val evaluator = CfgEvaluator.forCrate(crate)

    val cfgAttributes = QueryAttributes(if (attributeStub?.hasCfgAttr == false) {
        rawMetaItems
    } else {
        evaluator.expandCfgAttrs(rawMetaItems)
    }).cfgAttributes

    return evaluator.evaluate(cfgAttributes)
}

private val CFG_ATTRIBUTES_ENABLED_KEY = Registry.get("org.rust.lang.cfg.attributes")
