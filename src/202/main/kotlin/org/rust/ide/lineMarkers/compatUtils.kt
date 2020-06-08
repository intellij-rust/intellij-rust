/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.lineMarkers

import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.codeInsight.daemon.RelatedItemLineMarkerInfo

// BACKCOMPAT: 2020.1. Inline it
typealias SlowRunMarketResult = MutableCollection<in LineMarkerInfo<*>>
// BACKCOMPAT: 2020.1. Inline it
typealias NavigationMarkersResult = MutableCollection<in RelatedItemLineMarkerInfo<*>>
