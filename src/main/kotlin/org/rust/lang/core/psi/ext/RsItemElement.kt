/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.psi.ext

import com.intellij.psi.PsiElement
import org.rust.lang.core.macros.RsExpandedElement
import org.rust.lang.core.psi.*
import org.rust.lang.core.resolve.TYPES
import org.rust.lang.core.resolve.createProcessor
import org.rust.lang.core.resolve.processNestedScopesUpwards

/**
 * Note: don't forget to add an element type to [org.rust.lang.core.psi.RS_ITEMS]
 * when implementing [RsItemElement]
 */
interface RsItemElement : RsVisibilityOwner, RsOuterAttributeOwner, RsExpandedElement

fun <T : RsItemElement> Iterable<T>.filterInScope(scope: RsElement): List<T> {
    val set = toMutableSet()
    val processor = createProcessor {
        set.remove(it.element)
        set.isEmpty()
    }
    processNestedScopesUpwards(scope, TYPES, processor)
    return if (set.isEmpty()) toList() else toMutableList().apply { removeAll(set) }
}

val RsItemElement.itemKindName: String
    get() = when (this) {
        is RsMod, is RsModDeclItem -> "module"
        is RsFunction -> "function"
        is RsConstant -> when (kind) {
            RsConstantKind.STATIC -> "static"
            RsConstantKind.MUT_STATIC -> "static"
            RsConstantKind.CONST -> "constant"
        }
        is RsStructItem -> when (kind) {
            RsStructKind.STRUCT -> "struct"
            RsStructKind.UNION -> "union"
        }
        is RsEnumItem -> "enum"
        is RsTraitItem -> "trait"
        is RsTraitAlias -> "trait alias"
        is RsTypeAlias -> "type alias"
        is RsImplItem -> "impl"
        is RsUseItem -> "use item"
        is RsForeignModItem -> "foreign module"
        is RsExternCrateItem -> "extern crate"
        is RsMacro2 -> "macro"
        else -> "item"
    }

val RsItemElement.article: String
    get() = when (this) {
        is RsImplItem -> "an"
        else -> "a"
    }

val RsItemElement.itemDefKeyword: PsiElement
    get() = when (this) {
        is RsModItem -> mod
        is RsModDeclItem -> mod
        is RsFunction -> fn
        is RsConstant -> const ?: static ?: error("unknown constant type")
        is RsStructItem -> struct ?: union ?: error("unknown struct type")
        is RsEnumItem -> enum
        is RsTraitItem -> trait
        is RsTraitAlias -> trait
        is RsTypeAlias -> typeKw
        is RsImplItem -> impl
        is RsUseItem -> use
        is RsForeignModItem -> externAbi.extern
        is RsExternCrateItem -> extern
        is RsMacro2 -> macroKw
        else -> error("unknown item type")
    }
