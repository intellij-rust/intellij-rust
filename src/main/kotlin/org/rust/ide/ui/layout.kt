/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.ui

import com.intellij.openapi.ui.LabeledComponent
import com.intellij.ui.IdeBorderFactory
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.BorderLayout
import javax.swing.BoxLayout
import javax.swing.JComponent
import javax.swing.JPanel

const val HGAP = 30
const val VERTICAL_OFFSET = 2
const val HORIZONTAL_OFFSET = 5

fun layout(block: RsLayoutBuilder.() -> Unit): JPanel {
    val panel = JPanel(BorderLayout())
    val innerPanel = JPanel().apply {
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
    }
    panel.add(innerPanel, BorderLayout.NORTH)
    val builder = RsLayoutBuilderImpl(innerPanel).apply(block)
    UIUtil.mergeComponentsWithAnchor(builder.labeledComponents)
    return panel
}

interface RsLayoutBuilder {
    fun row(text: String = "", component: JComponent, toolTip: String = "")
    fun block(text: String, block: RsLayoutBuilder.() -> Unit)
}

private class RsLayoutBuilderImpl(
    val panel: JPanel,
    val labeledComponents: MutableList<LabeledComponent<*>> = mutableListOf()
) : RsLayoutBuilder {
    override fun block(text: String, block: RsLayoutBuilder.() -> Unit) {
        val blockPanel = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            border = IdeBorderFactory.createTitledBorder(text, false)
        }
        RsLayoutBuilderImpl(blockPanel, labeledComponents).apply(block)
        panel.add(blockPanel)
    }

    override fun row(text: String, component: JComponent, toolTip: String) {
        val labeledComponent = LabeledComponent.create(component, text, BorderLayout.WEST).apply {
            (layout as? BorderLayout)?.hgap = HGAP
            border = JBUI.Borders.empty(VERTICAL_OFFSET, HORIZONTAL_OFFSET)
        }
        labeledComponent.toolTipText = toolTip.trimIndent()
        labeledComponents += labeledComponent
        panel.add(labeledComponent)
    }
}
