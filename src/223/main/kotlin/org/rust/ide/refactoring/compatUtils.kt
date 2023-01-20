/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.refactoring

import com.intellij.openapi.util.Pair
import com.intellij.openapi.util.Pass
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement

typealias UsagesList = MutableList<Pair<PsiElement, TextRange>>
typealias RenameCallback = Pass<PsiElement>
