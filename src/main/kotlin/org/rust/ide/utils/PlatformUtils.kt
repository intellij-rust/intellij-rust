package org.rust.ide.utils

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Computable
import com.intellij.openapi.util.RecursionManager
import kotlin.reflect.KProperty

/**
 * Recursion guard
 */
fun <T> recursionGuard(key: Any, block: Computable<T>, memoize: Boolean = true): T? =
    RecursionManager.doPreventingRecursion(key, memoize, block)

/**
 * Wrapper util to run [runnable] under WL
 *
 * Copy pasted from IDEA for backwards compatibility with 15.0.4
 */
fun <T> runWriteAction(runnable: () -> T): T = ApplicationManager.getApplication().runWriteAction (Computable { runnable.invoke() })

/**
 * Wrapper util to run [runnable] under RL
 *
 * Copy pasted from IDEA for backwards compatibility with 15.0.4
 */
fun <T> runReadAction(runnable: () -> T): T = ApplicationManager.getApplication().runReadAction (Computable { runnable.invoke() })

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

class EdtOnly<T>(private var value: T) {
    operator fun getValue(thisRef: Any?, property: KProperty<*>): T {
        check(ApplicationManager.getApplication().isDispatchThread)
        return value
    }

    operator fun setValue(thisRef: Any?, property: KProperty<*>, newValue: T) {
        check(ApplicationManager.getApplication().isDispatchThread)
        value = newValue
    }
}
