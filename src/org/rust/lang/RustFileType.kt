package org.rust.lang

import com.intellij.icons.AllIcons
import com.intellij.lang.Language
import com.intellij.openapi.fileTypes.LanguageFileType
import org.jetbrains
import javax.swing.Icon

public open class RustFileType : LanguageFileType(RustLanguage.INSTANCE) {

    public object INSTANCE : RustFileType() {}

    private val DEFAULT_EXTENSION = "rst";

    override fun getName(): String = "Rust"

    override fun getIcon(): Icon? = AllIcons.FileTypes.Java;

    override fun getDefaultExtension(): String = DEFAULT_EXTENSION

    override fun getDescription(): String {
        return "Rust Files"
    }

}

