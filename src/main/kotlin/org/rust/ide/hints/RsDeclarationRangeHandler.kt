/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.hints

import com.intellij.codeInsight.hint.DeclarationRangeHandler
import com.intellij.openapi.util.TextRange
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.endOffset
import org.rust.lang.core.psi.ext.getPrevNonCommentSibling
import org.rust.lang.core.psi.ext.startOffset
import org.rust.lang.core.psi.ext.union

class RsStructItemDeclarationRangeHandler : DeclarationRangeHandler<RsStructItem> {
    override fun getDeclarationRange(container: RsStructItem): TextRange {
        val startOffset = (container.struct ?: container.union ?: container).startOffset
        val endOffset = (container.blockFields?.getPrevNonCommentSibling() ?: container).endOffset
        return TextRange(startOffset, endOffset)
    }
}

class RsTraitItemDeclarationRangeHandler : DeclarationRangeHandler<RsTraitItem> {
    override fun getDeclarationRange(container: RsTraitItem): TextRange {
        val startOffset = container.trait.startOffset
        val endOffset = (container.members?.getPrevNonCommentSibling() ?: container).endOffset
        return TextRange(startOffset, endOffset)
    }
}

class RsImplItemDeclarationRangeHandler : DeclarationRangeHandler<RsImplItem> {
    override fun getDeclarationRange(container: RsImplItem): TextRange {
        val startOffset = container.impl.startOffset
        val endOffset = (container.members?.getPrevNonCommentSibling() ?: container).endOffset
        return TextRange(startOffset, endOffset)
    }
}

class RsEnumItemDeclarationRangeHandler : DeclarationRangeHandler<RsEnumItem> {
    override fun getDeclarationRange(container: RsEnumItem): TextRange {
        val startOffset = container.enum.startOffset
        val endOffset = (container.enumBody?.getPrevNonCommentSibling() ?: container).endOffset
        return TextRange(startOffset, endOffset)
    }
}

class RsModItemDeclarationRangeHandler : DeclarationRangeHandler<RsModItem> {
    override fun getDeclarationRange(container: RsModItem): TextRange {
        val startOffset = container.mod.startOffset
        val endOffset = container.identifier.endOffset
        return TextRange(startOffset, endOffset)
    }
}

class RsFunctionDeclarationRangeHandler : DeclarationRangeHandler<RsFunction> {
    override fun getDeclarationRange(container: RsFunction): TextRange {
        val startOffset = container.fn.startOffset
        val endOffset = (container.block?.getPrevNonCommentSibling() ?: container).endOffset
        return TextRange(startOffset, endOffset)
    }
}

class RsMacroDeclarationRangeHandler : DeclarationRangeHandler<RsMacro> {
    override fun getDeclarationRange(container: RsMacro): TextRange =
        container.identifier?.textRange ?: container.textRange
}
