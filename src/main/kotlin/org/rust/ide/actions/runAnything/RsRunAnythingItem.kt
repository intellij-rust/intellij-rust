/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.actions.runAnything

import com.intellij.ide.actions.runAnything.items.RunAnythingItemBase
import com.intellij.openapi.util.text.StringUtil
import com.intellij.ui.SimpleColoredComponent
import com.intellij.ui.SimpleTextAttributes
import com.intellij.util.execution.ParametersListUtil
import java.awt.BorderLayout
import java.awt.Component
import javax.swing.Icon
import javax.swing.JPanel

abstract class RsRunAnythingItem(command: String, icon: Icon) : RunAnythingItemBase(command, icon) {
    abstract val helpCommand: String

    abstract val commandDescriptions: Map<String, String>

    abstract fun getOptionsDescriptionsForCommand(commandName: String): Map<String, String>?

    override fun createComponent(pattern: String?, isSelected: Boolean, hasFocus: Boolean): Component =
        super.createComponent(pattern, isSelected, hasFocus).also(this::customizeComponent)

    private fun customizeComponent(component: Component) {
        if (component !is JPanel) return

        val params = ParametersListUtil.parse(StringUtil.trimStart(command, helpCommand))
        val description = when (params.size) {
            0 -> null
            1 -> commandDescriptions[params.last()]
            else -> {
                val optionsDescriptions = getOptionsDescriptionsForCommand(params.first())
                optionsDescriptions?.get(params.last())
            }
        } ?: return
        val descriptionComponent = SimpleColoredComponent()
        descriptionComponent.append(
            StringUtil.shortenTextWithEllipsis(" $description.", 200, 0),
            SimpleTextAttributes.GRAYED_ITALIC_ATTRIBUTES
        )
        component.add(descriptionComponent, BorderLayout.EAST)
    }
}

