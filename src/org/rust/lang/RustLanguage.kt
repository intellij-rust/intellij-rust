package org.rust.lang

import com.intellij.lang.Language
import com.intellij.openapi.fileTypes.SyntaxHighlighter
import com.intellij.openapi.fileTypes.SyntaxHighlighterFactory
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile

public open class RustLanguage : Language {

    object INSTANCE : RustLanguage() {}

    constructor() : super("RUST") {
        SyntaxHighlighterFactory.LANGUAGE_FACTORY.addExplicitExtension(this, object: SyntaxHighlighterFactory() {
            override fun getSyntaxHighlighter(project: Project?, virtualFile: VirtualFile?): SyntaxHighlighter {
                return RustHighlighter();
            }
        })
    }

}

