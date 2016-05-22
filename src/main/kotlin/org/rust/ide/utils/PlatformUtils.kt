package org.rust.ide.utils

import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.project.Project


/**
 * Wrapper util to run [runnable] under WL
 *
 * NOTA BENE:   Both of the following utils purposefully DE-INLINED to make
 *              it compilable by the Kotlin 1.0.2 (due to internal compiler bug, see https://youtrack.jetbrains.com/issue/KT-12458)
 */
/* inline */ fun <T> runWriteAction(runnable: () -> T): T {
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
/* inline */ fun <T> runReadAction(runnable: () -> T): T {
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
