/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */
package org.rust.ide.refactoring.introduceParameter

import com.intellij.codeInsight.navigation.NavigationUtil
import com.intellij.codeInsight.unwrap.ScopeHighlighter
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.JBPopupListener
import com.intellij.openapi.ui.popup.LightweightWindowEvent
import com.intellij.openapiext.isUnitTestMode
import org.rust.ide.refactoring.MOCK
import org.rust.lang.core.psi.RsFunction
import org.rust.lang.core.psi.ext.title
import java.awt.Component
import java.util.concurrent.atomic.AtomicReference
import javax.swing.DefaultListCellRenderer
import javax.swing.JList
import javax.swing.ListSelectionModel

fun showEnclosingFunctionsChooser(editor: Editor,
                                  methods: List<RsFunction>,
                                  callback: (RsFunction) -> Unit) {
    if (isUnitTestMode && methods.size > 1) {
        callback(MOCK!!.chooseMethod(methods))
        return
    }
    val highlighter = AtomicReference(ScopeHighlighter(editor))
    val title = "Introduce parameter to method"
    val popup = JBPopupFactory.getInstance().createPopupChooserBuilder(methods)
        .setSelectionMode(ListSelectionModel.SINGLE_SELECTION)
        .setSelectedValue(methods.first(), true)
        .setAccessibleName(title)
        .setTitle(title)
        .setMovable(false)
        .setResizable(false)
        .setRequestFocus(true)
        .setItemChosenCallback { method ->
            callback(method)
        }
        .addListener(object : JBPopupListener {
            override fun onClosed(event: LightweightWindowEvent) {
                highlighter.getAndSet(null).dropHighlight()
            }
        })
        .setRenderer(object : DefaultListCellRenderer() {
            override fun getListCellRendererComponent(list: JList<*>,
                                                      value: Any?,
                                                      index: Int,
                                                      isSelected: Boolean,
                                                      cellHasFocus: Boolean): Component {
                val rendererComponent = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus)
                text = (value as RsFunction).title
                return rendererComponent
            }
        }).createPopup()
    popup.showInBestPositionFor(editor)
    val project = editor.project
    if (project != null) NavigationUtil.hidePopupIfDumbModeStarts(popup, project)
}
