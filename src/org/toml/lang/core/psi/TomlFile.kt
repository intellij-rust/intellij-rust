package org.toml.lang.core.psi

import com.intellij.extapi.psi.PsiFileBase
import com.intellij.openapi.fileTypes.FileType
import com.intellij.psi.FileViewProvider
import org.toml.lang.TomlFileType
import org.toml.lang.TomlLanguage

/**
 * @author Aleksey.Kladov
 */
class TomlFile(viewProvider: FileViewProvider) : PsiFileBase(viewProvider, TomlLanguage.INSTANCE) {
    override fun getFileType(): FileType =
            TomlFileType.INSTANCE

    override fun toString(): String =
            "TOML File"
}
