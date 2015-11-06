package org.toml.lang.icons

import com.intellij.ide.IconProvider
import com.intellij.psi.PsiElement
import org.toml.lang.core.psi.TomlFile
import javax.swing.Icon


public class TomlIconProvider : IconProvider() {
    override fun getIcon(element: PsiElement, flags: Int): Icon? {
        return when(element) {
            is TomlFile -> getFileIcon(element)
            else -> null
        }
    }

    fun getFileIcon(element: TomlFile): Icon? {
        return when(element.name) {
            "Cargo.toml" -> TomlIcons.CARGO_FILE
            else -> null
        }
    }
}


