/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.utils.evaluation

import com.intellij.openapiext.Testmark
import com.intellij.openapiext.isUnitTestMode
import org.rust.cargo.CfgOptions
import org.rust.cargo.project.workspace.FeatureState
import org.rust.cargo.project.workspace.PackageOrigin
import org.rust.lang.core.psi.RsMetaItem
import org.rust.lang.core.psi.ext.name
import org.rust.lang.core.psi.ext.value
import org.rust.lang.utils.evaluation.ThreeValuedLogic.*

// See https://doc.rust-lang.org/reference/conditional-compilation.html for more information


/**
 * Three-valued logic with the following rules:
 * - !Unknown = Unknown
 * - True && Unknown = Unknown
 * - False && Unknown = False
 * - Unknown && Unknown = Unknown
 * - True || Unknown = True
 * - False || Unknown = Unknown
 * - Unknown || Unknown = Unknown
 *
 * For more information, see https://en.wikipedia.org/wiki/Three-valued_logic
 */
enum class ThreeValuedLogic {
    True, False, Unknown;

    companion object {
        fun fromBoolean(value: Boolean) = when (value) {
            true -> True
            false -> False
        }
    }
}

infix fun ThreeValuedLogic.and(other: ThreeValuedLogic): ThreeValuedLogic = when (this) {
    True -> other
    False -> False
    Unknown -> if (other == False) False else Unknown
}

infix fun ThreeValuedLogic.or(other: ThreeValuedLogic): ThreeValuedLogic = when (this) {
    True -> True
    False -> other
    Unknown -> if (other == True) True else Unknown
}

operator fun ThreeValuedLogic.not(): ThreeValuedLogic = when (this) {
    True -> False
    False -> True
    Unknown -> Unknown
}

// Note, [options] and [packageOptions] can contain the same option values,
// i.e. it's possible to have both `unix` and `windows` values at the same time.
// See https://doc.rust-lang.org/reference/conditional-compilation.html#set-configuration-options
class CfgEvaluator(
    val options: CfgOptions,
    val packageOptions: CfgOptions,
    val features: Map<String, FeatureState>,
    val origin: PackageOrigin
) {
    fun evaluate(cfgAttributes: Sequence<RsMetaItem>): ThreeValuedLogic {
        val cfgPredicate = CfgPredicate.fromCfgAttributes(cfgAttributes)
        val result = evaluatePredicate(cfgPredicate)

        when (result) {
            True -> CfgTestmarks.evaluatesTrue.hit()
            False -> CfgTestmarks.evaluatesFalse.hit()
            Unknown -> Unit
        }

        return result
    }

    private fun evaluatePredicate(predicate: CfgPredicate): ThreeValuedLogic = when (predicate) {
        is CfgPredicate.All -> predicate.list.fold(True) { acc, pred -> acc and evaluatePredicate(pred) }
        is CfgPredicate.Any -> predicate.list.fold(False) { acc, pred -> acc or evaluatePredicate(pred) }
        is CfgPredicate.Not -> !evaluatePredicate(predicate.single)
        is CfgPredicate.NameOption -> evaluateName(predicate.name)
        is CfgPredicate.NameValueOption -> evaluateNameValue(predicate.name, predicate.value)
        is CfgPredicate.Feature -> evaluateFeature(predicate.name)
        is CfgPredicate.Error -> Unknown
    }

    private fun evaluateName(name: String): ThreeValuedLogic = when {
        name in packageOptions.nameOptions -> True
        // TODO: convert whitelist to blacklist and merge options with packageOption
        name in SUPPORTED_NAME_OPTIONS -> ThreeValuedLogic.fromBoolean(options.isNameEnabled(name))
        (name == "test" || name == "bootstrap") && origin == PackageOrigin.STDLIB -> False
        name == CfgOptions.TEST && isUnitTestMode -> ThreeValuedLogic.fromBoolean(options.isNameEnabled(name))
        else -> Unknown
    }

    private fun evaluateNameValue(name: String, value: String): ThreeValuedLogic = when {
        packageOptions.isNameValueEnabled(name, value) -> True
        // TODO: convert whitelist to blacklist and merge options with packageOption
        name in SUPPORTED_NAME_VALUE_OPTIONS -> ThreeValuedLogic.fromBoolean(options.isNameValueEnabled(name, value))
        else -> Unknown
    }

    private fun evaluateFeature(name: String): ThreeValuedLogic {
        if (origin == PackageOrigin.STDLIB) {
            // We don't have info about std features
            return Unknown
        }

        return when (features[name]) {
            FeatureState.Enabled -> True
            FeatureState.Disabled -> False
            null -> if (packageOptions.isNameValueEnabled("feature", name)) True else Unknown
        }
    }

    companion object {
        private val SUPPORTED_NAME_OPTIONS: Set<String> = setOf(
            "debug_assertions",
            "unix",
            "windows"
        )

        private val SUPPORTED_NAME_VALUE_OPTIONS: Set<String> = setOf(
            "target_arch",
            "target_endian",
            "target_env",
            "target_family",
            "target_feature",
            "target_os",
            "target_pointer_width",
            "target_vendor"
        )
    }
}

/** Configuration predicate */
private sealed class CfgPredicate {
    data class NameOption(val name: String) : CfgPredicate()
    data class NameValueOption(val name: String, val value: String) : CfgPredicate()
    data class Feature(val name: String) : CfgPredicate()
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

                // e.g. `#[cfg(feature = "my_feature")]`
                name == "feature" && value != null -> Feature(value)

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
