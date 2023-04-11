/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.refactoring.inlineTypeAlias

import org.rust.ide.refactoring.RsInlineDialog
import org.rust.lang.core.psi.RsTypeAlias
import org.rust.lang.core.resolve.ref.RsReference

class RsInlineTypeAliasDialog(
    private val typeAlias: RsTypeAlias,
    private val reference: RsReference?,
) : RsInlineDialog(typeAlias, reference, typeAlias.project) {

    init {
        init()
    }

    override fun getBorderTitle(): String = "Inline Type Alias"

    override fun getNameLabelText(): String = "Type alias ${typeAlias.name}"

    override fun getInlineAllText(): String = "Inline all and remove the type alias"

    override fun getInlineThisText(): String = "Inline this only and keep the type alias"

    override fun doAction() {
        val processor = RsInlineTypeAliasProcessor(project, typeAlias, reference, isInlineThisOnly)
        invokeRefactoring(processor)
    }
}
