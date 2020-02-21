package org.rust.ide.sdk.add

import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.ui.components.JBRadioButton
import com.intellij.util.ui.FormBuilder
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.BorderLayout
import javax.swing.ButtonGroup
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.JPanel

class RsAddSdkGroupPanel(
    name: String,
    panelIcon: Icon,
    val panels: List<RsAddSdkPanel>,
    defaultPanel: RsAddSdkPanel
) : RsAddSdkPanel() {
    override val panelName: String = name
    override val icon: Icon = panelIcon
    var selectedPanel: RsAddSdkPanel = defaultPanel
    private val changeListeners: MutableList<Runnable> = mutableListOf()

    override var newProjectPath: String?
        get() = selectedPanel.newProjectPath
        set(value) {
            for (panel in panels) {
                panel.newProjectPath = value
            }
        }

    init {
        layout = BorderLayout()
        val contentPanel = when (panels.size) {
            1 -> panels[0]
            else -> createRadioButtonPanel(panels, defaultPanel)
        }
        add(contentPanel, BorderLayout.NORTH)
    }

    override fun validateAll(): List<ValidationInfo> = panels.filter { it.isEnabled }.flatMap { it.validateAll() }

    override val sdk: Sdk?
        get() = selectedPanel.sdk

    override fun getOrCreateSdk(): Sdk? = selectedPanel.getOrCreateSdk()

    override fun addChangeListener(listener: Runnable) {
        changeListeners += listener
        for (panel in panels) {
            panel.addChangeListener(listener)
        }
    }

    private fun createRadioButtonPanel(panels: List<RsAddSdkPanel>, defaultPanel: RsAddSdkPanel): JPanel? {
        val buttonMap = panels.map { JBRadioButton(it.panelName) to it }.toMap(linkedMapOf())
        ButtonGroup().apply {
            for (button in buttonMap.keys) {
                add(button)
            }
        }
        val formBuilder = FormBuilder.createFormBuilder()
        for ((button, panel) in buttonMap) {
            panel.border = JBUI.Borders.emptyLeft(30)
            val name: JComponent = panel.nameExtensionComponent.let {
                JPanel(BorderLayout()).apply {
                    val inner = JPanel().apply {
                        add(button)
                        add(it)
                    }
                    add(inner, BorderLayout.WEST)
                }
            }
            formBuilder.addComponent(name)
            formBuilder.addComponent(panel)
            button.addItemListener {
                for (c in panels) {
                    UIUtil.setEnabled(c, c == panel, true)
                    c.nameExtensionComponent?.let {
                        UIUtil.setEnabled(it, c == panel, true)
                    }
                }
                if (button.isSelected) {
                    selectedPanel = panel
                    for (listener in changeListeners) {
                        listener.run()
                    }
                }
            }
        }
        buttonMap.filterValues { it == defaultPanel }.keys.first().isSelected = true
        return formBuilder.panel
    }
}
