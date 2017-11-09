/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.stubs

import com.intellij.psi.stubs.IStubElementType
import com.intellij.psi.stubs.StubBase
import com.intellij.psi.stubs.StubElement
import org.rust.lang.core.psi.ext.RsElement

abstract class RsElementStub<PsiT : RsElement>(
    parent: StubElement<*>?, elementType: IStubElementType<out StubElement<*>, *>?
) : StubBase<PsiT>(parent, elementType)
