package org.rust.lang.config;

import org.rust.lang.i18n.RustBundle;

import javax.swing.*;

public class RustConfigForm extends JPanel {
	private JLabel cargoBinaryLabel;
	private JTextField cargoBinary;

	public RustConfigForm() {
		SpringLayout layout = new SpringLayout();
		setLayout(layout);

		add(cargoBinaryLabel = new JLabel(RustBundle.INSTANCE.message("config.label.cargo-binary")));
		add(cargoBinary = new JTextField());

		layout.putConstraint(SpringLayout.WEST, cargoBinaryLabel, 5, SpringLayout.WEST, this);
		layout.putConstraint(SpringLayout.NORTH, cargoBinaryLabel, 5, SpringLayout.NORTH, this);

		layout.putConstraint(SpringLayout.WEST, cargoBinary, 5, SpringLayout.EAST, cargoBinaryLabel);
		layout.putConstraint(SpringLayout.NORTH, cargoBinary, 3, SpringLayout.NORTH, this);

		layout.putConstraint(SpringLayout.EAST, this, 5, SpringLayout.EAST, cargoBinary);
	}

	public String getCargoBinary() {
		return cargoBinary.getText();
	}

	public void setCargoBinary(String cargoBinary) {
		this.cargoBinary.setText(cargoBinary);
	}
}
