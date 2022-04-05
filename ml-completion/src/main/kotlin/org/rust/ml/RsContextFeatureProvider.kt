/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ml

import com.intellij.codeInsight.completion.ml.CompletionEnvironment
import com.intellij.codeInsight.completion.ml.ContextFeatureProvider
import com.intellij.codeInsight.completion.ml.MLFeatureValue
import org.rust.lang.core.macros.findExpansionElementOrSelf
import org.rust.lang.core.psi.RsExpr
import org.rust.lang.core.psi.ext.ancestorOrSelf
import org.rust.lang.core.psi.ext.isInAsyncContext
import org.rust.lang.core.psi.ext.isInConstContext
import org.rust.lang.core.psi.ext.isInUnsafeContext

/**
 * Note that there is a common context feature provider for all languages:
 * [com.intellij.completion.ml.common.CommonLocationFeatures].
 *
 * @see RsElementFeatureProvider
 */
@Suppress("UnstableApiUsage")
class RsContextFeatureProvider : ContextFeatureProvider {
    override fun getName(): String = "rust"

    override fun calculateFeatures(environment: CompletionEnvironment): Map<String, MLFeatureValue> {
        val result = hashMapOf<String, MLFeatureValue>()
        val position = environment.parameters.position.findExpansionElementOrSelf()
        val ancestorExpr = position.ancestorOrSelf<RsExpr>()

        result[IS_UNSAFE_CONTEXT] = MLFeatureValue.binary(ancestorExpr?.isInUnsafeContext == true)
        result[IS_ASYNC_CONTEXT] = MLFeatureValue.binary(ancestorExpr?.isInAsyncContext == true)
        result[IS_CONST_CONTEXT] = MLFeatureValue.binary(ancestorExpr?.isInConstContext == true)

        return result
    }

    companion object {
        private const val IS_UNSAFE_CONTEXT: String = "is_unsafe_context"
        private const val IS_ASYNC_CONTEXT: String = "is_async_context"
        private const val IS_CONST_CONTEXT: String = "is_const_context"
    }
}
