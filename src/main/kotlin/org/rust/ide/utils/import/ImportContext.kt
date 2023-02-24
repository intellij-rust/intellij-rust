/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.utils.import

import com.intellij.openapi.project.Project
import org.rust.lang.core.RsPsiPattern
import org.rust.lang.core.parser.RustParserUtil
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.*
import org.rust.lang.core.resolve.Namespace
import org.rust.lang.core.resolve.namespaces
import org.rust.lang.core.resolve2.CrateDefMap
import org.rust.lang.core.resolve2.ModData
import org.rust.lang.core.resolve2.RsModInfo
import org.rust.lang.core.resolve2.getModInfo

class ImportContext private constructor(
    /** Info of mod in which auto-import or completion is called */
    val rootInfo: RsModInfo,
    /** Mod in which auto-import or completion is called */
    val rootMod: RsMod,
    val type: Type,

    val pathInfo: PathInfo?,
) {
    val project: Project get() = rootInfo.project
    val rootModData: ModData get() = rootInfo.modData
    val rootDefMap: CrateDefMap get() = rootInfo.defMap

    companion object {
        fun from(path: RsPath, type: Type = Type.AUTO_IMPORT): ImportContext? =
            from(path, type, PathInfo.from(path, type == Type.COMPLETION))

        fun from(context: RsElement, type: Type = Type.AUTO_IMPORT, pathInfo: PathInfo? = null): ImportContext? {
            val rootMod = context.containingMod
            val info = getModInfo(rootMod) ?: return null
            return ImportContext(info, rootMod, type, pathInfo)
        }
    }

    enum class Type {
        AUTO_IMPORT,
        COMPLETION,
        OTHER,
    }

    class PathInfo private constructor(
        val rootPathText: String?,
        val rootPathParsingMode: RustParserUtil.PathParsingMode?,
        val rootPathAllowedNamespaces: Set<Namespace>?,
        val nextSegments: List<String>?,
        val namespaceFilter: (RsQualifiedNamedElement) -> Boolean,
        val parentIsMetaItem: Boolean,
    ) {
        companion object {
            fun from(path: RsPath, isCompletion: Boolean): PathInfo {
                val rootPath = path.rootPath().takeIf { it != path }
                return PathInfo(
                    rootPathText = rootPath?.text,
                    rootPathParsingMode = rootPath?.pathParsingMode,
                    rootPathAllowedNamespaces = rootPath?.allowedNamespaces(isCompletion),
                    nextSegments = path.getNextSegments(),
                    namespaceFilter = path.namespaceFilter(isCompletion),
                    parentIsMetaItem = path.parent is RsMetaItem,
                )
            }

            /**
             * foo1::foo2::foo3::foo4
             * ~~~~~~~~~~ this
             *             ~~~~~~~~~~ next segments
             */
            private fun RsPath.getNextSegments(): List<String>? {
                val parent = parent as? RsPath ?: return null
                return generateSequence(parent) { it.parent as? RsPath }
                    .mapTo(mutableListOf()) { it.referenceName ?: return null }
            }
        }
    }
}

@Suppress("RedundantLambdaArrow")
private fun RsPath.namespaceFilter(isCompletion: Boolean): (RsQualifiedNamedElement) -> Boolean = when (val context = context) {
    is RsTypeReference -> { e ->
        when (e) {
            is RsEnumItem,
            is RsStructItem,
            is RsTraitItem,
            is RsTypeAlias,
            is RsMacroDefinitionBase -> true
            else -> false
        }
    }
    is RsPathExpr -> { e ->
        when (e) {
            is RsEnumItem -> isCompletion
            // TODO: take into account fields type
            is RsFieldsOwner,
            is RsConstant,
            is RsFunction,
            is RsTypeAlias,
            is RsMacroDefinitionBase -> true
            else -> false
        }
    }
    is RsTraitRef -> { e -> e is RsTraitItem }
    is RsStructLiteral -> { e ->
        e is RsFieldsOwner && e.blockFields != null || e is RsTypeAlias
    }
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
    is RsPath -> { e -> Namespace.Types in e.namespaces }
    is RsMacroCall -> { e -> Namespace.Macros in e.namespaces }
    is RsMetaItem -> when {
        context.isRootMetaItem() -> { e ->
            e is RsFunction && e.isAttributeProcMacroDef
        }
        RsPsiPattern.derivedTraitMetaItem.accepts(context) -> { e ->
            e is RsFunction && e.isCustomDeriveProcMacroDef
        }
        else -> { _ -> true }
    }
    else -> { _ -> true }
}

val RsPath.pathParsingMode: RustParserUtil.PathParsingMode
    get() = when (parent) {
        is RsPathExpr,
        is RsStructLiteral,
        is RsPatStruct,
        is RsPatTupleStruct -> RustParserUtil.PathParsingMode.VALUE
        else -> RustParserUtil.PathParsingMode.TYPE
    }
