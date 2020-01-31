/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.debugger.settings

import com.intellij.ui.layout.Cell
import com.intellij.ui.layout.CellBuilder
import com.intellij.util.ui.UIUtil.ComponentStyle.SMALL
import javax.swing.JLabel

// BACKCOMPAT: 2019.3. inline this method
fun Cell.smallLabelWithGap(text: String): CellBuilder<JLabel> = label(text, style = SMALL).withLargeLeftGap()
