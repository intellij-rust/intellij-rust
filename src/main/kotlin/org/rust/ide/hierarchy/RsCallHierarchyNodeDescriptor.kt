/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.hierarchy

import com.intellij.icons.AllIcons
import com.intellij.ide.IdeBundle
import com.intellij.ide.hierarchy.HierarchyNodeDescriptor
import com.intellij.ide.util.treeView.NodeDescriptor
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.roots.ui.util.CompositeAppearance
import com.intellij.openapi.util.Comparing
import com.intellij.openapi.util.Iconable
import com.intellij.ui.LayeredIcon
import org.rust.lang.core.psi.RsEnumVariant
import org.rust.lang.core.psi.RsFunction
import org.rust.lang.core.psi.ext.*
import org.rust.lang.core.psi.substAndGetText
import org.rust.lang.core.types.emptySubstitution

class RsCallHierarchyNodeDescriptor(
    parentDescriptor: HierarchyNodeDescriptor?,
    element: RsQualifiedNamedElement,
    isBase: Boolean
) : HierarchyNodeDescriptor(element.project, parentDescriptor, element, isBase) {
    private var usageCount = 1

    fun incrementUsageCount() {
        usageCount += 1
    }

    override fun update(): Boolean {
        val oldText = myHighlightedText
        val oldIcon = icon

        var changes = super.update()

        val element = psiElement as RsQualifiedNamedElement
        val text = renderElement(element)

        val attributes: TextAttributes? = null
        myHighlightedText = CompositeAppearance()
        myHighlightedText.ending.addText(text, attributes)

        if (usageCount > 1) {
            myHighlightedText.ending.addText(
                " ${IdeBundle.message("node.call.hierarchy.N.usages", usageCount)}",
                getUsageCountPrefixAttributes(),
            )
        }

        val flags: Int = if (isMarkReadOnly) {
            Iconable.ICON_FLAG_VISIBILITY or Iconable.ICON_FLAG_READ_STATUS
        } else {
            Iconable.ICON_FLAG_VISIBILITY
        }

        val elementIcon = element.getIcon(flags)
        icon = if (changes && myIsBase) {
            LayeredIcon(2).apply {
                setIcon(elementIcon, 0)
                setIcon(AllIcons.General.Modified, 1, -AllIcons.General.Modified.iconWidth / 2, 0)
            }
        } else {
            elementIcon
        }

        val packageName = element.containingMod.qualifiedName
        if (packageName != null) {
            myHighlightedText.ending.addText("  ($packageName)", getPackageNameAttributes())
        }

        myName = myHighlightedText.text

        if (!(Comparing.equal(myHighlightedText, oldText) && Comparing.equal(icon, oldIcon))) {
            changes = true
        }

        return changes
    }
}

private fun renderElement(element: RsQualifiedNamedElement): String {
    return when (element) {
        is RsEnumVariant -> "${element.parentEnum.name}::${element.name}()"
        is RsMacroDefinitionBase -> "${element.name}!()"
        is RsFunction -> {
            when (val owner = element.owner) {
                is RsAbstractableOwner.Trait -> "${owner.trait.name}::${element.name}()"
                is RsAbstractableOwner.Impl -> {
                    val impl = owner.impl
                    val traitRef = impl.traitRef
                    var ownerName = impl.typeReference?.substAndGetText(emptySubstitution)

                    if (traitRef != null) {
                        ownerName = "<$ownerName as ${traitRef.resolveToTrait()?.name}>"
                    }
                    "${ownerName}::${element.name}()"
                }
                else -> "${element.name}()"
            }
        }
        else -> "${element.name}()"
    }
}

class SourceComparator private constructor() : Comparator<NodeDescriptor<*>> {
    override fun compare(nodeDescriptor1: NodeDescriptor<*>, nodeDescriptor2: NodeDescriptor<*>): Int =
        nodeDescriptor1.index.compareTo(nodeDescriptor2.index)

    companion object {
        val INSTANCE = SourceComparator()
    }
}
