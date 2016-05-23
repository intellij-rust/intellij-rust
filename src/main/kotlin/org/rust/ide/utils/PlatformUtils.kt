package org.rust.ide.utils

import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.project.Project


/**
 * Wrapper util to run [runnable] under WL
 */
fun <T> runWriteAction(runnable: () -> T): T {
    val token = WriteAction.start()
    try {
        return runnable()
    }
    finally {
        token.finish()
    }
}

/**
 * Wrapper util to run [runnable] under RL
 */
fun <T> runReadAction(runnable: () -> T): T {
    val token = ReadAction.start()
    try {
        return runnable()
    }
    finally {
        token.finish()
    }
}

/**
 * Util to extract application-level services
 */
inline fun <reified T : Any> service(): T = ServiceManager.getService(T::class.java)

/**
 * Util to extract project-level services
 */
inline fun <reified T : Any> Project.service(): T = ServiceManager.getService(this, T::class.java)
