/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.folding

import com.intellij.openapi.components.ServiceManager


abstract class RsCodeFoldingSettings {

    abstract var collapsibleOneLineMethods: Boolean

    companion object {
        val instance: RsCodeFoldingSettings get() = ServiceManager.getService(RsCodeFoldingSettings::class.java)
    }
}

