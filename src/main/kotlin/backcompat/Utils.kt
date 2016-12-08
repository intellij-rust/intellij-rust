package backcompat

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.Computable

/**
 * Wrapper util to run [runnable] under WL
 *
 * Copy pasted from IDEA for backwards compatibility with 15.0.4
 */
fun <T> runWriteAction(runnable: () -> T): T = ApplicationManager.getApplication().runWriteAction(Computable { runnable() })

/**
 * Wrapper util to run [runnable] under RL
 *
 * Copy pasted from IDEA for backwards compatibility with 15.0.4
 */
fun <T> runReadAction(runnable: () -> T): T = ApplicationManager.getApplication().runReadAction(Computable { runnable() })
