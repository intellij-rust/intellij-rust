/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.folding

import com.intellij.application.options.editor.CodeFoldingOptionsProvider
import com.intellij.openapi.options.BeanConfigurable
import org.rust.RsBundle

class RsCodeFoldingOptionsProvider :
    BeanConfigurable<RsCodeFoldingSettings>(RsCodeFoldingSettings.getInstance(), RsBundle.message("settings.rust.folding.title")),
    CodeFoldingOptionsProvider {

    init {
        val settings = instance
        if (settings != null) {
            checkBox(RsBundle.message("settings.rust.folding.one.line.methods.checkbox"), settings::collapsibleOneLineMethods)
        }
    }
}
