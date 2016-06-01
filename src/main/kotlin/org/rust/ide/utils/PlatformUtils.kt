package org.rust.ide.utils

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Computable
import com.intellij.openapi.util.RecursionManager

/**
 * Recursion guard
 */
fun <T> recursionGuard(key: Any, memoize: Boolean = true, block: () -> T): T? =
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
