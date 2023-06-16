/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.fixes

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import org.rust.lang.core.psi.RsOuterAttr
import org.rust.lang.core.psi.RsPsiFactory
import org.rust.lang.core.psi.ext.RsStructOrEnumItemElement
import org.rust.lang.core.psi.ext.findOuterAttr
import org.rust.lang.core.psi.ext.firstKeyword

class DeriveTraitsFix(
    item: RsStructOrEnumItemElement,
    private val traits: String,  // comma separated
) : RsQuickFixBase<RsStructOrEnumItemElement>(item) {

    private val itemName: String? = item.name

    override fun getText(): String = "Add #[derive($traits)] to `$itemName`"
    override fun getFamilyName(): String = "Derive trait"

    override fun invoke(project: Project, editor: Editor?, element: RsStructOrEnumItemElement) {
        invoke(element, traits)
    }

    companion object {
        fun invoke(item: RsStructOrEnumItemElement, traits: String) {
            val factory = RsPsiFactory(item.project)
            val existingDeriveAttr = item.findOuterAttr("derive")

            if (existingDeriveAttr != null) {
                updateDeriveAttr(factory, existingDeriveAttr, traits)
            } else {
                createDeriveAttr(factory, item, traits)
            }
        }

        private fun updateDeriveAttr(psiFactory: RsPsiFactory, deriveAttr: RsOuterAttr, traits: String) {
            val oldAttrText = deriveAttr.metaItem.text
            val newAttrText = oldAttrText.substringBeforeLast(")") + ", $traits)"

            val newDeriveAttr = psiFactory.createMetaItem(newAttrText)
            deriveAttr.metaItem.replace(newDeriveAttr)
        }

        private fun createDeriveAttr(psiFactory: RsPsiFactory, item: RsStructOrEnumItemElement, traits: String) {
            val keyword = item.firstKeyword!!
            val newAttrText = "derive($traits)"
            val newDeriveAttr = psiFactory.createOuterAttr(newAttrText)

            item.addBefore(newDeriveAttr, keyword)
        }
    }
}
