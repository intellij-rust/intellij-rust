/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.navigation

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.util.NlsContexts
import com.intellij.psi.PsiElement

// BACKCOMPAT: 2023.1. Inline it
fun getPsiElementPopup(elements: Array<PsiElement>, title: @NlsContexts.PopupTitle String?): JBPopup {
    return com.intellij.codeInsight.navigation.getPsiElementPopup(elements, title)
}

// BACKCOMPAT: 2023.1. Inline it
fun hidePopupIfDumbModeStarts(popup: JBPopup, project: Project) {
    com.intellij.codeInsight.navigation.hidePopupIfDumbModeStarts(popup, project)
}
