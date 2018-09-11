/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.utils

import com.intellij.openapi.util.ModificationTracker
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReference
import org.rust.lang.core.psi.rustStructureModificationTracker
import java.lang.ref.WeakReference

/**
 * This modification tracker hold 2 values: the psi reference and its target.
 * It will be incremented if the reference starts to point to another target
 */
class ReferenceTargetModificationTracker(
    private val reference: PsiReference
) : ModificationTracker {

    private var target: WeakReference<PsiElement>? = null
    private var modCount: Long = 0

    override fun getModificationCount(): Long {
        if (!reference.element.isValid) return ++modCount

        val newTarget = reference.resolve()
        if (newTarget != target?.get()) {
            target = newTarget?.let { WeakReference(it) }
            return ++modCount
        }

        return modCount
    }

    companion object {
        /**
         * Creates [ReferenceTargetModificationTracker] for some reference that
         * depends on [rustStructureModificationTracker] (i.e. some reference,
         * that is guaranteed to be resolved to the same target until
         * [rustStructureModificationTracker] is not incremented).
         * Modification tracker, created with this function, will have a better
         * performance compared to the one created with its constructor
         */
        fun forRustStructureDependentReference(
            reference: PsiReference
        ): ModificationTracker {
            return reference.element.project.rustStructureModificationTracker
                .and(ReferenceTargetModificationTracker(reference))
        }
    }
}

/**
 * This modification tracker increments only if both trackers are incremented.
 * The main feature of this modification tracker is that it is lazy. I.e. it will
 * not check modification count of the second tracker if the count of the first tracker
 * is remained the same. It may be useful if computation of the modification count
 * of the second tracker is too expensive.
 */
private class AndModificationTracker(
    private val left: ModificationTracker,
    private val right: ModificationTracker
) : ModificationTracker {
    private var lastLeftModCount: Long = -1
    private var lastRightModCount: Long = -1

    override fun getModificationCount(): Long {
        val newLeftModCount = left.modificationCount
        if (newLeftModCount == lastLeftModCount) return lastRightModCount
        lastLeftModCount = newLeftModCount

        lastRightModCount = right.modificationCount
        return lastRightModCount
    }
}

/** @see AndModificationTracker */
fun ModificationTracker.and(other: ModificationTracker): ModificationTracker =
    AndModificationTracker(this, other)
