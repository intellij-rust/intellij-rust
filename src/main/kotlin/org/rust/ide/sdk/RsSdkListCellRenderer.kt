/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.sdk

import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.projectRoots.SdkModificator
import com.intellij.ui.ColoredListCellRenderer
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.TitledSeparator
import com.intellij.util.ui.JBUI
import org.rust.ide.sdk.RsSdkRenderingUtils.icon
import org.rust.ide.sdk.RsSdkRenderingUtils.name
import org.rust.ide.sdk.RsSdkRenderingUtils.noToolchainMarker
import org.rust.ide.sdk.RsSdkRenderingUtils.path
import java.awt.Component
import javax.swing.JList

class RsSdkListCellRenderer(
    private val sdkModifiers: Map<Sdk, SdkModificator>? = null,
    private val nullSdkName: String = noToolchainMarker,
    private val nullSdkValue: Sdk? = null
) : ColoredListCellRenderer<Any>() {

    override fun getListCellRendererComponent(
        list: JList<out Any>?,
        value: Any?,
        index: Int,
        selected: Boolean,
        hasFocus: Boolean
    ): Component = when (value) {
        SEPARATOR -> TitledSeparator(null).apply {
            border = JBUI.Borders.empty()
        }
        else -> super.getListCellRendererComponent(list, value, index, selected, hasFocus)
    }

    override fun customizeCellRenderer(
        list: JList<out Any>,
        value: Any?,
        index: Int,
        selected: Boolean,
        hasFocus: Boolean
    ) {
        when (value) {
            is Sdk -> {
                val sdkModificator = sdkModifiers?.get(value)
                appendName(value, name(value, sdkModificator), sdkModificator)
                icon = icon(value)
            }
            is String -> append(value)
            null -> {
                if (nullSdkValue != null) {
                    appendName(nullSdkValue, name(nullSdkValue, nullSdkName))
                    icon = icon(nullSdkValue)
                } else {
                    append(nullSdkName)
                }
            }
        }
    }

    private fun appendName(sdk: Sdk, name: SdkName, sdkModificator: SdkModificator? = null) {
        if (name.modifier != null) {
            append("[${name.modifier}] ${name.primary}", SimpleTextAttributes.ERROR_ATTRIBUTES)
        } else {
            append(name.primary)
        }

        if (name.secondary != null) {
            append(" ${name.secondary}", SimpleTextAttributes.GRAYED_SMALL_ATTRIBUTES)
        }

        path(sdk, sdkModificator)?.let { append(" $it", SimpleTextAttributes.GRAYED_SMALL_ATTRIBUTES) }
    }

    companion object {
        const val SEPARATOR: String = "separator"
    }
}
