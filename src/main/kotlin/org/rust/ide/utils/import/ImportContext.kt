/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.utils.import

import com.intellij.openapi.project.Project
import com.intellij.psi.search.GlobalSearchScope
import org.rust.ide.search.RsWithMacrosProjectScope
import org.rust.lang.core.parser.RustParserUtil
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.*

@Suppress("DataClassPrivateConstructor")
data class ImportContext private constructor(
    val project: Project,
    val mod: RsMod,
    val superMods: LinkedHashSet<RsMod>,
    val scope: GlobalSearchScope,
    val pathParsingMode: RustParserUtil.PathParsingMode,
    val attributes: RsFile.Attributes,
    val namespaceFilter: (RsQualifiedNamedElement) -> Boolean
) {
    companion object {
        fun from(project: Project, path: RsPath, isCompletion: Boolean): ImportContext = ImportContext(
            project = project,
            mod = path.containingMod,
            superMods = LinkedHashSet(path.containingMod.superMods),
            scope = RsWithMacrosProjectScope(project),
            pathParsingMode = path.pathParsingMode,
            attributes = path.stdlibAttributes,
            namespaceFilter = path.namespaceFilter(isCompletion)
        )

        fun from(project: Project, element: RsElement): ImportContext = ImportContext(
            project = project,
            mod = element.containingMod,
            superMods = LinkedHashSet(element.containingMod.superMods),
            scope = RsWithMacrosProjectScope(project),
            pathParsingMode = RustParserUtil.PathParsingMode.TYPE,
            attributes = element.stdlibAttributes,
            namespaceFilter = { true }
        )
    }
}

private fun RsPath.namespaceFilter(isCompletion: Boolean): (RsQualifiedNamedElement) -> Boolean = when (context) {
    is RsTypeReference -> { e ->
        when (e) {
            is RsEnumItem,
            is RsStructItem,
            is RsTraitItem,
            is RsTypeAlias -> true
            else -> false
        }
    }
    is RsPathExpr -> { e ->
        when (e) {
            is RsEnumItem -> isCompletion
            // TODO: take into account fields type
            is RsFieldsOwner,
            is RsConstant,
            is RsFunction -> true
            else -> false
        }
    }
    is RsTraitRef -> { e -> e is RsTraitItem }
    is RsStructLiteral -> { e -> e is RsFieldsOwner && e.blockFields != null }
    is RsPatBinding -> { e ->
        when (e) {
            is RsEnumItem,
            is RsEnumVariant,
            is RsStructItem,
            is RsTypeAlias,
            is RsConstant,
            is RsFunction -> true
            else -> false
        }
    }
    else -> { _ -> true }
}

private val RsPath.pathParsingMode: RustParserUtil.PathParsingMode
    get() = when (parent) {
        is RsPathExpr,
        is RsStructLiteral,
        is RsPatStruct,
        is RsPatTupleStruct -> RustParserUtil.PathParsingMode.VALUE
        else -> RustParserUtil.PathParsingMode.TYPE
    }
