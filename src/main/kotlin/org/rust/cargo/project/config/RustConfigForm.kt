package org.rust.cargo.project.config

import org.rust.lang.i18n.RustBundle
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JTextField
import javax.swing.SpringLayout

class RustConfigForm : JPanel() {
    private var cargoBinaryLabel: JLabel;
    private var cargoBinaryField: JTextField;

    init {
        val layout = SpringLayout()
        setLayout(layout)
        cargoBinaryLabel = JLabel(RustBundle.message("config.label.cargo-binary"))
        cargoBinaryField = JTextField()
        add(cargoBinaryLabel)
        add(cargoBinaryField)

        layout.putConstraint(SpringLayout.WEST, cargoBinaryLabel, 5, SpringLayout.WEST, this)
        layout.putConstraint(SpringLayout.NORTH, cargoBinaryLabel, 5, SpringLayout.NORTH, this)

        layout.putConstraint(SpringLayout.WEST, cargoBinaryField, 5, SpringLayout.EAST, cargoBinaryLabel)
        layout.putConstraint(SpringLayout.NORTH, cargoBinaryField, 3, SpringLayout.NORTH, this)

        layout.putConstraint(SpringLayout.EAST, this, 5, SpringLayout.EAST, cargoBinaryField)
    }

    var cargoBinary: String
        get() = cargoBinaryField.text
        set(value) {
            this.cargoBinaryField.text = value
        }
}
