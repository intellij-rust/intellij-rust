/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.refactoring

import com.intellij.openapi.util.Pair
import com.intellij.openapi.util.Pass
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement

// BACKCOMPAT: 2022.3. Inline it
typealias UsagesList = MutableList<in Pair<PsiElement, TextRange>>
// BACKCOMPAT: 2022.3. Inline it
typealias RenameCallback = Pass<in PsiElement>
