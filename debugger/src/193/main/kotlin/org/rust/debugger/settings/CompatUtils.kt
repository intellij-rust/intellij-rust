/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

@file:Suppress("UnstableApiUsage")

package org.rust.debugger.settings

import com.intellij.ui.components.Label
import com.intellij.ui.layout.Cell
import com.intellij.ui.layout.CellBuilder
import com.intellij.util.ui.UIUtil.ComponentStyle.SMALL
import javax.swing.JLabel

fun Cell.smallLabelWithGap(text: String): CellBuilder<JLabel> = Label(text, style = SMALL)(gapLeft = 4)
