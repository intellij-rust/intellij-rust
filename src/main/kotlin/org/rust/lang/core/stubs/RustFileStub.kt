package org.rust.lang.core.stubs

import com.intellij.psi.stubs.PsiFileStubImpl
import org.rust.lang.core.RustFileElementType
import org.rust.lang.core.psi.impl.RustFile

class RustFileStub : PsiFileStubImpl<RustFile> {
    val hasNoStdAttr: Boolean

    constructor(file: RustFile) : this(file, file.hasNoStdAttr)

    constructor(file: RustFile?, hasNoStdAttr: Boolean) : super(file) {
        this.hasNoStdAttr = hasNoStdAttr
    }

    override fun getType() = RustFileElementType
}
