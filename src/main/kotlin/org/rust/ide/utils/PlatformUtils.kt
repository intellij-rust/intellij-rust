package org.rust.ide.utils

import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.project.Project

inline fun <T> runWriteAction(runnable: () -> T): T {
    val token = WriteAction.start()
    try {
        return runnable()
    }
    finally {
        token.finish()
    }
}

inline fun <T> runReadAction(runnable: () -> T): T {
    val token = ReadAction.start()
    try {
        return runnable()
    }
    finally {
        token.finish()
    }
}

inline fun <reified T : Any> service(): T = ServiceManager.getService(T::class.java)

inline fun <reified T : Any> Project.service(): T = ServiceManager.getService(this, T::class.java)
