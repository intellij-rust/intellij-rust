/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.psi

import com.google.common.annotations.VisibleForTesting
import com.intellij.psi.impl.source.PsiFileImpl
import com.intellij.psi.stubs.PsiFileStub
import org.rust.openapiext.isUnitTestMode
import java.lang.reflect.Method

object RsPsiFileInternals {
    @VisibleForTesting
    val setStubTreeMethod: Method? = try {
        PsiFileImpl::class.java
            .getDeclaredMethod("setStubTree", PsiFileStub::class.java)
            .apply { isAccessible = true }
    } catch (e: Throwable) {
        if (isUnitTestMode) throw e else null
    }

    fun setStubTree(file: RsFile, stub: PsiFileStub<*>): Boolean {
        return if (setStubTreeMethod != null) {
            setStubTreeMethod.invoke(file, stub)
            true
        } else {
            false
        }
    }
}
