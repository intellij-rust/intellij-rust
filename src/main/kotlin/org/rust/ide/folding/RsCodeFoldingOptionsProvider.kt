/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.folding

import com.intellij.application.options.editor.CodeFoldingOptionsProvider
import com.intellij.openapi.options.BeanConfigurable

class RsCodeFoldingOptionsProvider :
    BeanConfigurable<RsCodeFoldingSettings>(RsCodeFoldingSettings.instance),
    CodeFoldingOptionsProvider {

    init {
        val settings = instance

        checkBox("Rust one-line methods",
            { settings.collapsibleOneLineMethods },
            { v -> settings.collapsibleOneLineMethods = v })
        checkBox("Rust raw-identifiers",
            { settings.hideRawKeywordsPrefix },
            { v -> settings.hideRawKeywordsPrefix = v })
    }
}
