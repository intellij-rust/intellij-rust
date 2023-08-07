/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.mir

import com.intellij.openapi.util.Key
import com.intellij.psi.util.CachedValue
import com.intellij.psi.util.CachedValuesManager
import org.rust.lang.core.mir.schemas.MirBody
import org.rust.lang.core.psi.RsConstant
import org.rust.lang.core.psi.RsFunction
import org.rust.lang.core.psi.ext.RsInferenceContextOwner
import org.rust.lang.core.psi.ext.createCachedResult

val RsInferenceContextOwner.mirBody: MirBody?
    get() {
        return CachedValuesManager.getCachedValue(this, MIR_KEY) {
            val mirBody = try {
                when (this) {
                    is RsFunction -> MirBuilder.build(this)
                    is RsConstant -> MirBuilder.build(this)
                    else -> null // TODO support building MIR for more cases
                }
            } catch (ignored: NotImplementedError) {
                null
            } catch (ignored: IllegalStateException) {
                // TODO use a special exception class if we can't build MIR
                null
            }
            createCachedResult(mirBody)
        }
    }

private val MIR_KEY: Key<CachedValue<MirBody?>> = Key.create("org.rust.lang.core.mir.MIR_KEY")
