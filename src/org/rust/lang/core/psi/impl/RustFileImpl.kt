package org.rust.lang.core.psi.impl

import com.intellij.extapi.psi.PsiFileBase
import com.intellij.openapi.fileTypes.FileType
import com.intellij.psi.FileViewProvider
import org.rust.lang.RustFileType
import org.rust.lang.RustLanguage
import org.rust.lang.core.resolve.scope.RustResolveScope

public class RustFileImpl(fileViewProvider: FileViewProvider)
    : PsiFileBase(fileViewProvider, RustLanguage.INSTANCE)
    , RustResolveScope {

    override fun getFileType(): FileType = RustFileType.INSTANCE;

}