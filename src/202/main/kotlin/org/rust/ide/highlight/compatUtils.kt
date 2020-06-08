/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.highlight

import com.intellij.psi.PsiElement
import com.intellij.util.Consumer

// BACKCOMPAT: 2020.1. Inline it
typealias ExitPointSelectionConsumer = Consumer<in List<PsiElement>>
