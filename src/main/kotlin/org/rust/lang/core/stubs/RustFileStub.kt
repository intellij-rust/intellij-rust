package org.rust.lang.core.stubs

import com.intellij.psi.stubs.PsiFileStubImpl
import org.rust.lang.core.psi.impl.RustFileImpl

class RustFileStub(file: RustFileImpl) : PsiFileStubImpl<RustFileImpl>(file)
