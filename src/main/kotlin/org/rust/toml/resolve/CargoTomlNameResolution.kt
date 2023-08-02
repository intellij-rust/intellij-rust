/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.toml.resolve

import com.intellij.psi.PsiElementResolveResult
import com.intellij.psi.ResolveResult
import org.rust.toml.*
import org.toml.lang.psi.*
import org.toml.lang.psi.ext.TomlLiteralKind
import org.toml.lang.psi.ext.kind

fun TomlFile.resolveFeature(featureName: String, depOnly: Boolean = false): Array<ResolveResult> =
    allFeatures(depOnly)
        .filter { it.text == featureName }
        .map { PsiElementResolveResult(it) }
        .toList()
        .toTypedArray()

fun TomlFile.allFeatures(depOnly: Boolean = false): Sequence<TomlKeySegment> {
    val explicitFeatures = hashSetOf<String>()
    return tableList
        .asSequence()
        .flatMap { table ->
            val header = table.header
            when {
                // [features]
                header.isFeatureListHeader && !depOnly -> {
                    table.entries
                        .asSequence()
                        .mapNotNull { it.key.segments.singleOrNull() }
                        .map { key ->
                            key.name?.let { explicitFeatures += it }
                            key
                        }
                }

                // # Optional dependencies are features too:
                // [dependencies]
                // bar = { version = "*", optional = true }
                header.isDependencyListHeader -> {
                    table.entries
                        .asSequence()
                        .filter {
                            (it.value as? TomlInlineTable)?.getValueWithKey("optional")?.asBoolean() == true
                        }
                        .mapNotNull { it.key.segments.singleOrNull() }
                        .filter { it.name !in explicitFeatures }
                }

                // [dependencies.bar]
                // version = "*"
                // optional = true
                header.isSpecificDependencyTableHeader -> {
                    if (table.getValueWithKey("optional")?.asBoolean() == true) {
                        val lastKey = header.key?.segments?.last()
                        if (lastKey != null && lastKey.name !in explicitFeatures) {
                            sequenceOf(lastKey)
                        } else {
                            emptySequence()
                        }
                    } else {
                        emptySequence()
                    }
                }

                else -> {
                    emptySequence()
                }
            }
        }
        .constrainOnce()
}

private fun TomlValue.asBoolean(): Boolean? =
    ((this as? TomlLiteral)?.kind as? TomlLiteralKind.Boolean)?.value

private val TomlLiteralKind.Boolean.value: Boolean
    get() = node.text == "true"
