/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.intentions

import com.intellij.codeInsight.hint.HintManager
import com.intellij.codeInsight.intention.impl.QuickEditAction
import com.intellij.openapi.Disposable
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.ex.util.EditorUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.Balloon
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.JBPopupListener
import com.intellij.openapi.ui.popup.LightweightWindowEvent
import com.intellij.openapi.util.Disposer
import com.intellij.ui.EditorTextField
import com.intellij.ui.awt.RelativePoint
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import org.rust.lang.RsFileType
import org.rust.lang.core.psi.RsCodeFragment
import org.rust.openapiext.document
import java.awt.Point
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import java.awt.event.KeyListener

object RsCodeFragmentPopup {
    /**
     * Shows a popup with a code fragment.
     * @param validate: Validate the entered value.
     *  If it returns null, the value is OK.
     *  If it returns a string, it is displayed as an error.
     */
    fun show(
        editor: Editor,
        project: Project,
        codeFragment: RsCodeFragment,
        title: String,
        onComplete: () -> Unit,
        validate: (() -> String?)? = null,
    ) {
        val editorTextField = createEditorTextField(project, codeFragment) ?: return
        showBalloon(editor, project, editorTextField, onComplete, validate, title)
    }

    private fun createEditorTextField(project: Project, codeFragment: RsCodeFragment): EditorTextField? {
        val document = codeFragment.containingFile.document ?: return null
        val editorTextField = object : EditorTextField(document, project, RsFileType, false, true) {
            override fun createEditor(): EditorEx {
                val editor = super.createEditor()
                editor.setHorizontalScrollbarVisible(false)
                editor.setVerticalScrollbarVisible(false)
                editor.settings.isUseSoftWraps = false
                editor.settings.lineCursorWidth = EditorUtil.getDefaultCaretWidth()
                editor.colorsScheme.editorFontName = font.fontName
                editor.colorsScheme.editorFontSize = font.size
                return editor
            }

            override fun addNotify() {
                super.addNotify()
                val editor = editor ?: return
                for (listener in keyListeners) {
                    editor.contentComponent.addKeyListener(listener)
                }
                editor.contentComponent.focusTraversalKeysEnabled = false
            }

            @Synchronized
            override fun removeKeyListener(l: KeyListener?) {
                super.removeKeyListener(l)
                editor?.contentComponent?.removeKeyListener(l)
            }
        }
        editorTextField.setFontInheritedFromLAF(false)
        editorTextField.font = EditorUtil.getEditorFont()
        return editorTextField
    }

    private fun showBalloon(
        editor: Editor,
        parent: Disposable,
        editorTextField: EditorTextField,
        onComplete: () -> Unit,
        validate: (() -> String?)?,
        title: String
    ) {
        val balloon = JBPopupFactory.getInstance().createBalloonBuilder(editorTextField)
            .setShadow(true)
            .setAnimationCycle(0)
            .setHideOnAction(false)
            .setHideOnKeyOutside(false)
            .setFillColor(UIUtil.getPanelBackground())
            .setBorderInsets(JBUI.insets(3))
            .setTitle(title)
            .createBalloon()

        Disposer.register(parent, balloon)

        val keyListener = object : KeyAdapter() {
            override fun keyPressed(e: KeyEvent) {
                when (e.keyCode) {
                    KeyEvent.VK_ENTER -> {
                        val error = validate?.invoke()
                        if (error == null) {
                            balloon.hide()
                            onComplete()
                        } else {
                            HintManager.getInstance().showErrorHint(editor, error)
                        }
                    }
                    KeyEvent.VK_ESCAPE -> balloon.hide()
                }
            }
        }
        editorTextField.addKeyListener(keyListener)

        val fontMetrics = editorTextField.getFontMetrics(editorTextField.font)
        val minimalWidth = fontMetrics.stringWidth("1234")
        editorTextField.setPreferredWidth(minimalWidth)
        val documentListener = object : DocumentListener {
            override fun documentChanged(event: DocumentEvent) {
                val textWidth = fontMetrics.stringWidth(editorTextField.text)
                editorTextField.setPreferredWidth(minimalWidth + textWidth)
                balloon.revalidate()
            }
        }
        editorTextField.addDocumentListener(documentListener)

        balloon.addListener(object : JBPopupListener {
            override fun onClosed(event: LightweightWindowEvent) {
                editorTextField.removeKeyListener(keyListener)
                editorTextField.removeDocumentListener(documentListener)
            }
        })

        val position = QuickEditAction.getBalloonPosition(editor)
        var point = JBPopupFactory.getInstance().guessBestPopupLocation(editor)
        if (position == Balloon.Position.above) {
            val p = point.point
            point = RelativePoint(point.component, Point(p.x, p.y - editor.lineHeight))
        }
        balloon.show(point, position)
        editorTextField.requestFocus()
    }
}
