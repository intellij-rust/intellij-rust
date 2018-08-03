/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.openapiext

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.util.containers.ContainerUtil
import java.lang.ref.ReferenceQueue
import java.lang.ref.WeakReference

/** Allows to track garbage collection of java objects */
interface ObjectGcTracker {
    /**
     * Allows to track garbage collection of [obj] object. [hook] will be called
     * not earlier than [obj] is garbage collected. [hook] will be called using
     * [com.intellij.openapi.application.Application.executeOnPooledThread].
     *
     * Note: [hook] must NOT refer to [obj]. If you do so, [obj] will never be GCed.
     *
     * Note: It is not guaranteed that [hook] will be called.
     */
    fun registerObjectGcHook(obj: Any, hook: Runnable)

    fun registerObjectGcHook(obj: Any, hook: () -> Unit) =
        registerObjectGcHook(obj, Runnable { hook() })

    companion object {
        val instance: ObjectGcTracker
            get() = ServiceManager.getService(ObjectGcTracker::class.java)
    }
}

/**
 * Implementation details:
 *
 * We use JVM magic from [java.lang.ref] package. See
 * [documentation](https://docs.oracle.com/javase/8/docs/api/java/lang/ref/package-summary.html)
 *
 * The main part is [ReferenceWithCallback]. It extends JVMs [WeakReference], which
 * has one useful feature: when referenced object is GCed, the [WeakReference] will
 * be enqueued to [ReferenceQueue]. In our case, [ReferenceWithCallback] will be
 * enqueued to [clearedReferencesQueue]. Then we can poll [ReferenceWithCallback]
 * from the queue and do anything with a data attached to it. The interesting
 * thing is that [ReferenceWithCallback] can be GCed itself, so we must provide
 * a strong reference to it until it is placed to queue. We use
 * [anchoredReferences] to provide such strong references.
 */
class ObjectGcTrackerImpl : ObjectGcTracker, Disposable {
    private val clearedReferencesQueue = ReferenceQueue<Any>()
    private val anchoredReferences = ContainerUtil.newConcurrentSet<ReferenceWithCallback>()
    private val thread = Thread(::processQueue, "ObjectGcTrackerImpl callback caller")

    init {
        thread.isDaemon = true
        thread.start()
    }

    override fun registerObjectGcHook(obj: Any, hook: Runnable) {
        anchoredReferences.add(ReferenceWithCallback(obj, clearedReferencesQueue, hook))
    }

    private fun processQueue() {
        while (!Thread.interrupted()) {
            val ref = try {
                // `remove()` method is like `.poll()`, but instead of returning null
                // it awaits new entry if queue is empty
                clearedReferencesQueue.remove() as ReferenceWithCallback
            } catch (e: InterruptedException) {
                break
            }

            anchoredReferences.remove(ref)

            try {
                ApplicationManager.getApplication().executeOnPooledThread(ref.callback)
            } catch (t: Throwable) {
                LOG.error(t)
            }
        }
    }

    override fun dispose() {
        thread.interrupt()
    }

    private class ReferenceWithCallback(
        referent: Any,
        q: ReferenceQueue<Any>,
        val callback: Runnable
    ) : WeakReference<Any>(referent, q)
}

private val LOG = Logger.getInstance("org.rust.openapiext.ObjectGcTrackerImpl")
