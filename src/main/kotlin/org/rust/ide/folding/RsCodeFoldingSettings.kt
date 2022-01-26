/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.folding

import com.intellij.openapi.components.service


abstract class RsCodeFoldingSettings {

    abstract var collapsibleOneLineMethods: Boolean

    companion object {
        fun getInstance(): RsCodeFoldingSettings = service()
    }
}

