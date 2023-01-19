/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.lineMarkers

import com.intellij.ide.util.PsiElementListCellRenderer
import com.intellij.openapi.util.Computable
import com.intellij.openapi.util.NotNullLazyValue
import com.intellij.psi.SmartPsiElementPointer

typealias Pointers = NotNullLazyValue<List<SmartPsiElementPointer<*>>>
typealias CellRenderer = Computable<PsiElementListCellRenderer<*>>
