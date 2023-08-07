/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.navigation

import com.intellij.codeInsight.navigation.NavigationUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.util.NlsContexts
import com.intellij.psi.PsiElement

fun getPsiElementPopup(elements: Array<PsiElement>, title: @NlsContexts.PopupTitle String?): JBPopup {
    return NavigationUtil.getPsiElementPopup(elements, title)
}

fun hidePopupIfDumbModeStarts(popup: JBPopup, project: Project) {
    NavigationUtil.hidePopupIfDumbModeStarts(popup, project)
}
