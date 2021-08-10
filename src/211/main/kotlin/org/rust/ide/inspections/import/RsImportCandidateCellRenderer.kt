/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.inspections.import

import com.intellij.util.ui.UIUtil
import java.awt.Component
import javax.swing.BorderFactory
import javax.swing.DefaultListCellRenderer
import javax.swing.JList
import javax.swing.SwingConstants

class RsImportCandidateCellRenderer : RsImportCandidateCellRendererBase() {

    private val rightRender: LibraryCellRender = LibraryCellRender()

    override fun getRightCellRenderer(value: Any?): DefaultListCellRenderer = rightRender

    private inner class LibraryCellRender : DefaultListCellRenderer() {
        override fun getListCellRendererComponent(
            list: JList<*>,
            value: Any?,
            index: Int,
            isSelected: Boolean,
            cellHasFocus: Boolean
        ): Component {
            val component = super.getListCellRendererComponent(list, null, index, isSelected, cellHasFocus)
            val textWithIcon = textWithIcon(value)
            if (textWithIcon != null) {
                text = textWithIcon.first
                icon = textWithIcon.second
            }

            border = BorderFactory.createEmptyBorder(0, 0, 0, 2)
            horizontalTextPosition = SwingConstants.LEFT
            background = UIUtil.getListBackground(isSelected, cellHasFocus)
            foreground = UIUtil.getListForeground(isSelected, cellHasFocus)
            return component
        }
    }
}
