/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.psi.ext

import com.intellij.openapi.diagnostic.Logger
import com.intellij.psi.NavigatablePsiElement
import com.intellij.psi.PsiElement
import org.rust.cargo.project.workspace.CargoWorkspace
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.CfgTestmarks.evaluatesFalse
import org.rust.lang.core.psi.ext.CfgTestmarks.evaluatesTrue
import org.rust.openapiext.Testmark

private val LOG = Logger.getInstance(RsDocAndAttributeOwner::class.java)

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
    val outerAttrList: List<RsOuterAttr>
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

/**
 * Returns [QueryAttributes] for given PSI element.
 */
val RsDocAndAttributeOwner.queryAttributes: QueryAttributes
    get() = QueryAttributes(this)

/**
 * Allows for easy querying [RsDocAndAttributeOwner] for specific attributes.
 *
 * **Do not instantiate directly**, use [RsDocAndAttributeOwner.queryAttributes] instead.
 */
class QueryAttributes(
    private val psi: RsDocAndAttributeOwner
) {
    private val attributes: Sequence<RsAttr> = Sequence { (psi as? RsInnerAttributeOwner)?.innerAttrList.orEmpty().iterator() } +
        Sequence { (psi as? RsOuterAttributeOwner)?.outerAttrList.orEmpty().iterator() }

    // #[doc(hidden)]
    val isDocHidden: Boolean get() = hasAttributeWithArg("doc", "hidden")

    // #[cfg(test)], #[cfg(target_has_atomic = "ptr")], #[cfg(all(target_arch = "wasm32", not(target_os = "emscripten")))]
    fun hasCfgAttr(): Boolean {
        if (psi is RsFunction) {
            val stub = psi.greenStub
            if (stub != null) return stub.isCfg
        }
        // TODO: We probably want this optimization also for other items that we query regularly..

        return hasAttribute("cfg")
    }

    // Returns true when the #[cfg(...)] attribute evaluates to true or is not present
    fun evaluateCfgAttr(): Boolean {
        if (!hasCfgAttr())
            return true

        // TODO: When we open both cargo projects for an application and a library,
        // this will return the library as containing package.
        // When the application now requests certain features, which are not enabled by default in the library
        // we will evaluate features wrongly here
        val containing = psi.containingCargoPackage
        val cfgData = containing?.cfgData ?: return true

        val attributeParsed = cfgAttributeParsed ?: return true
        val result = evaluate(attributeParsed, cfgData)
        when (result) {
            true -> evaluatesTrue.hit()
            false -> evaluatesFalse.hit()
        }
        // Debug evaluation, will cause problems during test runs as it accesses the PSI
        // LOG.info("$result ${attrsByName("cfg").joinToString(separator = " + ") { "#[${it.text}]" }} on ${psi.javaClass.name} in ${containing.name}")
        return result
    }

    val cfgAttributeParsed: Cfg?
        get() {
            val cfgAttributes = attrsByName("cfg")
            if (cfgAttributes.count() == 0) return null
            //TODO How to handle parsing errors?
            val parsedCfgs = cfgAttributes.mapNotNull {
                // Skip the first metaItem and metaItemArgs
                it.metaItemArgs?.metaItemList?.firstOrNull()?.let { parseMetaItem(it) }
            }

            if (parsedCfgs.count() == 1) return parsedCfgs.first()
            // In case of multiple #[cfg(xxx)] #[cfg(yyy)] attributes they are ANDed together
            return Cfg.All(parsedCfgs.toList())
        }

    private fun debugMissingBoolCfg(name: String) {
        if (name == "stage0" || name == "windows" || name == "test")
            return

        LOG.info("Unknown boolean cfg entry $name")
    }

    private fun evaluate(cfg: Cfg, data: CargoWorkspace.CfgData): Boolean = when (cfg) {
        is Cfg.All -> cfg.list.all { evaluate(it, data) }
        is Cfg.Any -> cfg.list.any { evaluate(it, data) } //Does short circuit evaluation
        is Cfg.Not -> !evaluate(cfg.single, data)
        is Cfg.CheckBoolean -> {
            val value = data.boolCfg.get(cfg.name)
            if (value == null) {
                debugMissingBoolCfg(cfg.name)
                false
            } else
                value
        }
        is Cfg.CheckString -> {
            data.stringCfg.filter { it.first == cfg.name }.any { it.second == cfg.value }
        }
        is Cfg.Error -> true //Assume it is true
    }

    // `#[attributeName]`, `#[attributeName(arg)]`, `#[attributeName = "Xxx"]`
    fun hasAttribute(attributeName: String): Boolean {
        val attrs = attrsByName(attributeName)
        return attrs.any()
    }

    fun hasAnyOfOuterAttributes(vararg attributes: String): Boolean {
        val outerAttrList = (psi as? RsOuterAttributeOwner)?.outerAttrList ?: return false
        return outerAttrList.any { it.metaItem.name in attributes }
    }

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

    // `#[attributeName = "Xxx"]`
    private fun getStringAttributes(attributeName: String): Sequence<String?> = attrsByName(attributeName).map { it.value }

    val metaItems: Sequence<RsMetaItem>
        get() = attributes.mapNotNull { it.metaItem }

    /**
     * Get a sequence of all attributes named [name]
     */
    private fun attrsByName(name: String): Sequence<RsMetaItem> = metaItems.filter { it.name == name }

    override fun toString(): String =
        "QueryAttributes(${attributes.joinToString { it.text }})"

    sealed class Cfg {
        class Not(val single: Cfg) : Cfg()
        class Any(val list: List<Cfg>) : Cfg()
        class All(val list: List<Cfg>) : Cfg()
        class CheckBoolean(val name: String) : Cfg()
        class CheckString(val name: String, val value: String) : Cfg()
        class Error : Cfg()
    }

    fun parseMetaItem(metaItem: RsMetaItem): Cfg? {
        val args = metaItem.metaItemArgs
        val value = metaItem.value
        val name = metaItem.name
        return when {
            args != null -> {
                // identifier(...)
                when {
                    name == "not" -> {
                        val list = args.metaItemList.mapNotNull { parseMetaItem(it) }
                        val v = list.singleOrNull() ?: Cfg.Error()
                        Cfg.Not(v)
                    }
                    name == "any" -> {
                        val list = args.metaItemList.mapNotNull { parseMetaItem(it) }
                        Cfg.Any(list)
                    }
                    name == "all" -> {
                        val list = args.metaItemList.mapNotNull { parseMetaItem(it) }
                        Cfg.All(list)
                    }
                    else -> {
                        // ERROR
                        Cfg.Error()
                    }
                }
            }
            name != null && value != null -> {
                // identifier = "value"
                Cfg.CheckString(name, value)
            }
            //TODO We cannot put all the rest into else, if eq != null
            name != null -> {
                // identifier
                Cfg.CheckBoolean(name)
            }
            else -> Cfg.Error()
        }
    }
}

object CfgTestmarks {
    val evaluatesTrue = Testmark("evaluatesTrue")
    val evaluatesFalse = Testmark("evaluatesFalse")
}
