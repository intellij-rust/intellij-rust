/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang

import com.intellij.openapi.fileTypes.LanguageFileType
import com.intellij.openapi.vfs.VirtualFile
import org.rust.ide.icons.RsIcons
import javax.swing.Icon

object RsFileType : LanguageFileType(RsLanguage) {

    object DEFAULTS {
        val EXTENSION: String = "rs"
    }

    override fun getName(): String = "Rust"

    override fun getIcon(): Icon = RsIcons.RUST_FILE

    override fun getDefaultExtension(): String = DEFAULTS.EXTENSION

    override fun getCharset(file: VirtualFile, content: ByteArray): String = "UTF-8"

    override fun getDescription(): String = "Rust Files"
}

