package org.rust.ide.utils

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Computable
import com.intellij.openapi.util.RecursionManager

/**
 * Recursion guard
 */
fun <T> recursionGuard(key: Any, block: Computable<T>, memoize: Boolean = true): T? =
    RecursionManager.doPreventingRecursion(key, memoize, block)

/**
 * Util to extract application-level services
 */
inline fun <reified T : Any> service(): T = ServiceManager.getService(T::class.java)

/**
 * Util to extract project-level services
 */
inline fun <reified T : Any> Project.service(): T = ServiceManager.getService(this, T::class.java)


fun checkWriteAccessAllowed() {
    check(ApplicationManager.getApplication().isWriteAccessAllowed) {
        "Needs write action"
    }
}
