package org.rust.lang.core.psi.impl

import com.intellij.extapi.psi.PsiFileBase
import com.intellij.openapi.fileTypes.FileType
import com.intellij.psi.FileViewProvider
import org.rust.lang.RustFileType
import org.rust.lang.RustLanguage

public class RustPsiFileImpl(fileViewProvider: FileViewProvider) : PsiFileBase(fileViewProvider, RustLanguage.INSTANCE) {

    override fun getFileType(): FileType = RustFileType.INSTANCE;

}