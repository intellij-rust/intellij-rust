/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.lineMarkers

import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.codeInsight.daemon.RelatedItemLineMarkerInfo
import com.intellij.psi.PsiElement

typealias SlowRunMarketResult = MutableCollection<LineMarkerInfo<PsiElement>>
typealias NavigationMarkersResult = MutableCollection<in RelatedItemLineMarkerInfo<PsiElement>>
