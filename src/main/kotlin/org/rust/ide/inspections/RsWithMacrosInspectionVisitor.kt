/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.inspections

import org.rust.lang.core.macros.prepareForExpansionHighlighting
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.RsAttrProcMacroOwner
import org.rust.stdext.removeLast

/**
 * This is a non-recursive element visitor, but if it faces a macro invocation, it accepts all elements
 * expanded from the macro. This visitor is intended to be used in [RsLocalInspectionTool] implementations.
 */
abstract class RsWithMacrosInspectionVisitor : RsVisitor() {
    private var processingMacros: Boolean = false

    final override fun visitConstant(o: RsConstant) {
        visitConstant2(o)
        visitMacroExpansion(o)
    }

    open fun visitConstant2(o: RsConstant) {
        super.visitConstant(o)
    }

    final override fun visitEnumItem(o: RsEnumItem) {
        visitEnumItem2(o)
        visitMacroExpansion(o)
    }

    open fun visitEnumItem2(o: RsEnumItem) {
        super.visitEnumItem(o)
    }

    final override fun visitExternCrateItem(o: RsExternCrateItem) {
        visitExternCrateItem2(o)
        visitMacroExpansion(o)
    }

    open fun visitExternCrateItem2(o: RsExternCrateItem) {
        super.visitExternCrateItem(o)
    }

    final override fun visitForeignModItem(o: RsForeignModItem) {
        visitForeignModItem2(o)
        visitMacroExpansion(o)
    }

    open fun visitForeignModItem2(o: RsForeignModItem) {
        super.visitForeignModItem(o)
    }

    final override fun visitFunction(o: RsFunction) {
        visitFunction2(o)
        visitMacroExpansion(o)
    }

    open fun visitFunction2(o: RsFunction) {
        super.visitFunction(o)
    }

    final override fun visitImplItem(o: RsImplItem) {
        visitImplItem2(o)
        visitMacroExpansion(o)
    }

    open fun visitImplItem2(o: RsImplItem) {
        super.visitImplItem(o)
    }

    final override fun visitMacro(o: RsMacro) {
        visitMacro2(o)
        visitMacroExpansion(o)
    }

    open fun visitMacro2(o: RsMacro) {
        super.visitMacro(o)
    }

    final override fun visitMacro2(o: RsMacro2) {
        visitMacro22(o)
        visitMacroExpansion(o)
    }

    open fun visitMacro22(o: RsMacro2) {
        super.visitMacro2(o)
    }

    final override fun visitMacroCall(o: RsMacroCall) {
        visitMacroCall2(o)
        visitMacroExpansion(o)
    }

    open fun visitMacroCall2(o: RsMacroCall) {
        super.visitMacroCall(o)
    }

    final override fun visitModItem(o: RsModItem) {
        visitModItem2(o)
        visitMacroExpansion(o)
    }

    open fun visitModItem2(o: RsModItem) {
        super.visitModItem(o)
    }

    final override fun visitStructItem(o: RsStructItem) {
        visitStructItem2(o)
        visitMacroExpansion(o)
    }

    open fun visitStructItem2(o: RsStructItem) {
        super.visitStructItem(o)
    }

    final override fun visitTraitAlias(o: RsTraitAlias) {
        visitTraitAlias2(o)
        visitMacroExpansion(o)
    }

    open fun visitTraitAlias2(o: RsTraitAlias) {
        super.visitTraitAlias(o)
    }

    final override fun visitTraitItem(o: RsTraitItem) {
        visitTraitItem2(o)
        visitMacroExpansion(o)
    }

    open fun visitTraitItem2(o: RsTraitItem) {
        super.visitTraitItem(o)
    }

    final override fun visitTypeAlias(o: RsTypeAlias) {
        visitTypeAlias2(o)
        visitMacroExpansion(o)
    }

    open fun visitTypeAlias2(o: RsTypeAlias) {
        super.visitTypeAlias(o)
    }

    final override fun visitUseItem(o: RsUseItem) {
        visitUseItem2(o)
        visitMacroExpansion(o)
    }

    open fun visitUseItem2(o: RsUseItem) {
        super.visitUseItem(o)
    }

    private fun visitMacroExpansion(item: RsAttrProcMacroOwner) {
        if (processingMacros) return

        val preparedMacro = item.procMacroAttribute.attr?.prepareForExpansionHighlighting() ?: return
        val macros = mutableListOf(preparedMacro)

        processingMacros = true

        while (macros.isNotEmpty()) {
            val macro = macros.removeLast()
            for (element in macro.elementsForHighlighting) {
                element.accept(this)
                if (element is RsAttrProcMacroOwner) {
                    macros += element.procMacroAttribute.attr?.prepareForExpansionHighlighting(macro) ?: continue
                }
            }
        }

        processingMacros = false
    }
}
