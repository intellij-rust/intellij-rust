/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.template

import com.intellij.codeInsight.template.impl.DefaultLiveTemplatesProvider

class RsLiveTemplatesProvider : DefaultLiveTemplatesProvider {
    override fun getDefaultLiveTemplateFiles(): Array<out String> = arrayOf(
        "/org/rust/ide/liveTemplates/iterations",
        "/org/rust/ide/liveTemplates/output",
        "/org/rust/ide/liveTemplates/test",
        "/org/rust/ide/liveTemplates/other"
    )

    override fun getHiddenLiveTemplateFiles(): Array<out String>? = null
}
