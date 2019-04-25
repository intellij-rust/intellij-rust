/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang

import com.intellij.psi.PsiLanguageInjectionHost
import com.intellij.util.messages.Topic
import org.rust.lang.core.psi.ext.RsElement

interface RsDebugInjectionListener {
    data class DebugContext(var element: RsElement? = null)

    fun evalDebugContext(host: PsiLanguageInjectionHost, context: DebugContext)

    fun didInject(host: PsiLanguageInjectionHost)

    companion object {
        @JvmField
        val INJECTION_TOPIC = Topic.create("Rust Language Injected", RsDebugInjectionListener::class.java)
    }
}
