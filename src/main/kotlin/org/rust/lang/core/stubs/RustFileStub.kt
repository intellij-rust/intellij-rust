package org.rust.lang.core.stubs

import com.intellij.psi.stubs.PsiFileStubImpl
import org.rust.lang.core.psi.impl.RustFile

class RustFileStub(file: RustFile) : PsiFileStubImpl<RustFile>(file)
