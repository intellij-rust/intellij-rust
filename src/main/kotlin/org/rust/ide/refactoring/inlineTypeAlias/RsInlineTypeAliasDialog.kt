/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.refactoring.inlineTypeAlias

import org.rust.RsBundle
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

    override fun getBorderTitle(): String = RsBundle.message("border.title.inline.type.alias")

    override fun getNameLabelText(): String = RsBundle.message("label.type.alias", typeAlias.name?:"")

    override fun getInlineAllText(): String = RsBundle.message("radio.inline.all.remove.type.alias")

    override fun getInlineThisText(): String = RsBundle.message("radio.inline.this.only.keep.type.alias")

    override fun doAction() {
        val processor = RsInlineTypeAliasProcessor(project, typeAlias, reference, isInlineThisOnly)
        invokeRefactoring(processor)
    }
}
