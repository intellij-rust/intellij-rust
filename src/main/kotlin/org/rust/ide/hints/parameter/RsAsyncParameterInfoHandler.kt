/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.hints.parameter

import com.intellij.lang.parameterInfo.CreateParameterInfoContext
import com.intellij.lang.parameterInfo.ParameterInfoHandler
import com.intellij.lang.parameterInfo.UpdateParameterInfoContext
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.ReadAction
import com.intellij.openapiext.isUnitTestMode
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.util.concurrency.AppExecutorUtil
import org.rust.lang.core.psi.ext.startOffset
import java.util.concurrent.Callable
import java.util.concurrent.Executor

/**
 * This is a hack to make [ParameterInfoHandler] asynchronous. Usual [ParameterInfoHandler] is called from the EDT
 * and so complex computations inside it (e.g. name resolution) can freeze the UI.
 */
abstract class RsAsyncParameterInfoHandler<ParameterOwner : PsiElement, ParameterType : Any>
    : ParameterInfoHandler<ParameterOwner, ParameterType> {

    abstract fun findTargetElement(file: PsiFile, offset: Int): ParameterOwner?

    /**
     * Ran in background thread
     */
    abstract fun calculateParameterInfo(element: ParameterOwner): Array<ParameterType>?

    final override fun findElementForParameterInfo(context: CreateParameterInfoContext): ParameterOwner? {
        val element = findTargetElement(context.file, context.offset) ?: return null

        return if (isUnitTestMode) {
            context.itemsToShow = calculateParameterInfo(element) ?: return null
            element
        } else {
            ReadAction.nonBlocking(Callable {
                calculateParameterInfo(element)
            }).finishOnUiThread(ModalityState.defaultModalityState()) { paramInfo ->
                if (paramInfo != null) {
                    context.itemsToShow = paramInfo
                    showParameterInfo(element, context)
                }
            }.expireWhen { !element.isValid }.submit(executor)

            null
        }
    }

    final override fun findElementForUpdatingParameterInfo(context: UpdateParameterInfoContext): ParameterOwner? {
        return findTargetElement(context.file, context.offset)
    }

    /**
     * This method is not called by the platform b/c we always return null from [findElementForParameterInfo].
     * We call it manually from [findElementForParameterInfo] and from unit tests.
     */
    override fun showParameterInfo(element: ParameterOwner, context: CreateParameterInfoContext) {
        context.showHint(element, element.startOffset, this)
    }

    private companion object {
        private val executor: Executor =
            AppExecutorUtil.createBoundedApplicationPoolExecutor("Rust async parameter info handler", 1)
    }
}
