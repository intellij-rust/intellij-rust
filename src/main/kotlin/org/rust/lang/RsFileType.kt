/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang

import com.intellij.openapi.fileTypes.LanguageFileType
import com.intellij.openapi.vfs.VirtualFile
import org.rust.RsBundle
import org.rust.ide.icons.RsIcons
import javax.swing.Icon

object RsFileType : LanguageFileType(RsLanguage) {

    override fun getName(): String = "Rust"

    override fun getIcon(): Icon = RsIcons.RUST_FILE

    override fun getDefaultExtension(): String = "rs"

    override fun getCharset(file: VirtualFile, content: ByteArray): String = "UTF-8"

    override fun getDescription(): String = RsBundle.message("label.rust.files")
}
