/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.psi.ext

import com.intellij.openapi.util.Key
import com.intellij.psi.util.CachedValue
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import org.rust.lang.core.macros.RsExpandedElement
import org.rust.lang.core.macros.decl.MacroGraph
import org.rust.lang.core.macros.decl.MacroGraphBuilder
import org.rust.lang.core.psi.MacroBraces
import org.rust.lang.core.psi.RsMacroBody
import org.rust.lang.doc.documentation
import org.rust.stdext.HashCode
import java.util.*

/**
 * [org.rust.lang.core.psi.RsMacro] or [org.rust.lang.core.psi.RsMacro2]
 */
interface RsMacroDefinitionBase : RsNameIdentifierOwner,
                                  RsQualifiedNamedElement,
                                  RsOuterAttributeOwner,
                                  RsExpandedElement,
                                  RsModificationTrackerOwner {
    val macroBodyStubbed: RsMacroBody?
    val bodyHash: HashCode?
    val hasRustcBuiltinMacro: Boolean

    val preferredBraces: MacroBraces
}

/**
 * Analyses documentation of macro definition to determine what kind of brackets usually used
 */
fun RsMacroDefinitionBase.guessPreferredBraces(): MacroBraces {
    val documentation = documentation()
    if (documentation.isEmpty()) return MacroBraces.PARENS

    val map: MutableMap<MacroBraces, Int> = EnumMap(MacroBraces::class.java)
    for (result in MACRO_CALL_PATTERN.findAll(documentation)) {
        if (result.groups["name"]?.value != name) continue
        val braces = MacroBraces.values().find { it.openText == result.groups["brace"]?.value } ?: continue
        map.merge(braces, 1, Int::plus)
    }

    return map.maxByOrNull { it.value }?.key ?: MacroBraces.PARENS
}

private val MACRO_CALL_PATTERN: Regex = """(^|[^\p{Alnum}_])(r#)?(?<name>\w+)\s*!\s*(?<brace>[({\[])""".toRegex()

private val MACRO_GRAPH_KEY: Key<CachedValue<MacroGraph?>> = Key.create("MACRO_GRAPH_KEY")

val RsMacroDefinitionBase.graph: MacroGraph?
    get() = CachedValuesManager.getCachedValue(this, MACRO_GRAPH_KEY) {
        val graph = MacroGraphBuilder(this).build()
        CachedValueProvider.Result.create(graph, modificationTracker)
    }
