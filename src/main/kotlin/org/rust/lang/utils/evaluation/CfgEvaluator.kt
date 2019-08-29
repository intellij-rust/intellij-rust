/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.utils.evaluation

import org.rust.cargo.CfgOptions
import org.rust.lang.core.psi.RsMetaItem
import org.rust.lang.core.psi.ext.name
import org.rust.lang.core.psi.ext.value
import org.rust.openapiext.Testmark

// See https://doc.rust-lang.org/reference/conditional-compilation.html for more information

class CfgEvaluator(val options: CfgOptions) {
    fun evaluate(cfgAttributes: Sequence<RsMetaItem>): Boolean {
        val cfgPredicate = CfgPredicate.fromCfgAttributes(cfgAttributes)
        val result = evaluatePredicate(cfgPredicate)

        when (result) {
            true -> CfgTestmarks.evaluatesTrue.hit()
            false -> CfgTestmarks.evaluatesFalse.hit()
        }
        return result
    }

    private fun evaluatePredicate(predicate: CfgPredicate): Boolean = when (predicate) {
        is CfgPredicate.All -> predicate.list.all(::evaluatePredicate)
        is CfgPredicate.Any -> predicate.list.any(::evaluatePredicate)
        is CfgPredicate.Not -> !evaluatePredicate(predicate.single)
        is CfgPredicate.NameOption -> options.isNameEnabled(predicate.name)
        is CfgPredicate.NameValueOption -> options.isNameValueEnabled(predicate.name, predicate.value)
        is CfgPredicate.Error -> true
    }
}

/** Configuration predicate */
private sealed class CfgPredicate {
    data class NameOption(val name: String) : CfgPredicate()
    data class NameValueOption(val name: String, val value: String) : CfgPredicate()
    class All(val list: List<CfgPredicate>) : CfgPredicate()
    class Any(val list: List<CfgPredicate>) : CfgPredicate()
    class Not(val single: CfgPredicate) : CfgPredicate()
    object Error : CfgPredicate()

    companion object {
        fun fromCfgAttributes(cfgAttributes: Sequence<RsMetaItem>): CfgPredicate {
            val cfgPredicates = cfgAttributes
                .mapNotNull { it.metaItemArgs?.metaItemList?.firstOrNull() } // `unix` in `#[cfg(unix)]`
                .map(Companion::fromMetaItem)

            return when (val predicate = cfgPredicates.singleOrNull()) {
                is CfgPredicate -> predicate
                null -> All(cfgPredicates.toList())
            }
        }

        private fun fromMetaItem(metaItem: RsMetaItem): CfgPredicate {
            val args = metaItem.metaItemArgs
            val name = metaItem.name
            val value = metaItem.value

            return when {
                // e.g. `#[cfg(any(foo, bar))]`
                args != null -> {
                    val predicates = args.metaItemList.mapNotNull { fromMetaItem(it) }
                    when (name) {
                        "all" -> All(predicates)
                        "any" -> Any(predicates)
                        "not" -> Not(predicates.singleOrNull()
                            ?: Error)
                        else -> Error
                    }
                }

                // e.g. `#[cfg(target_os = "macos")]`
                name != null && value != null -> NameValueOption(name, value)

                // e.g. `#[cfg(unix)]`
                name != null -> NameOption(name)

                else -> Error
            }
        }
    }
}

object CfgTestmarks {
    val evaluatesTrue = Testmark("evaluatesTrue")
    val evaluatesFalse = Testmark("evaluatesFalse")
}
