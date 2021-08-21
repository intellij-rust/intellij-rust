/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rustSlowTests.cargo.runconfig.buildtool

import com.intellij.build.BuildViewManager
import com.intellij.build.events.BuildEvent
import com.intellij.build.events.FinishBuildEvent
import com.intellij.build.events.OutputBuildEvent
import com.intellij.build.events.StartBuildEvent
import com.intellij.openapi.project.Project
import org.rustSlowTests.cargo.runconfig.waitFinished
import java.util.concurrent.CountDownLatch

class TestBuildViewManager(project: Project) : BuildViewManager(project) {
    private val latch: CountDownLatch = CountDownLatch(1)

    var rootNode: EventTreeNode? = null

    private val events = mutableMapOf<Any, MutableList<EventTreeNode>>()

    override fun onEvent(buildId: Any, event: BuildEvent) {
        super.onEvent(buildId, event)

        if (event is OutputBuildEvent) return

        if (event.id !in events) {
            val childrenEvents = mutableListOf<EventTreeNode>()
            events[event.id] = childrenEvents
            val parentId = event.parentId
            val parentNode = ParentEventNode(childrenEvents)
            if (parentId != null) {
                events.getValue(parentId).add(parentNode)
            }
            if (event is StartBuildEvent) {
                rootNode = parentNode
            }
        }

        events.getValue(event.id) += SingleEventNode(event)

        if (event is FinishBuildEvent) {
            latch.countDown()
        }
    }

    @Throws(InterruptedException::class)
    fun waitFinished(timeoutMs: Long = 5000) {
        latch.waitFinished(timeoutMs)
    }

    sealed class EventTreeNode(val unorderedGroupId: Int) {

        abstract val firstMessage: String
        abstract fun treeRepresentation(builder: StringBuilder, level: Int)

        override fun toString(): String {
            val builder = StringBuilder()
            treeRepresentation(builder, 0)
            return builder.toString()
        }
    }

    @Suppress("EqualsOrHashCode")
    class SingleEventNode(val event: BuildEvent, unorderedGroupId: Int = -1) : EventTreeNode(unorderedGroupId) {

        override val firstMessage: String get() = event.message

        override fun treeRepresentation(builder: StringBuilder, level: Int) {
            builder.append(" ".repeat(level))
            builder.append(event.toString())
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as SingleEventNode

            if (event != other.event) return false

            return true
        }
    }

    @Suppress("EqualsOrHashCode")
    class ParentEventNode(
        val children: List<EventTreeNode>,
        unorderedGroupId: Int = -1
    ) : EventTreeNode(unorderedGroupId) {

        override val firstMessage: String get() = children.first().firstMessage

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as ParentEventNode

            if (children.size != other.children.size) return false

            var index = 0
            while (index < children.size) {
                val node = children[index]
                val unorderedGroupId = node.unorderedGroupId

                fun List<EventTreeNode>.sortIfNeeded(): List<EventTreeNode> {
                    return if (unorderedGroupId != -1) sortedBy { it.firstMessage } else this
                }

                val startIndex = index
                while (index < children.size && children[index].unorderedGroupId == unorderedGroupId) {
                    index++
                }

                val subChildren = children.subList(startIndex, index).sortIfNeeded()
                val otherSubChildren = other.children.subList(startIndex, index).sortIfNeeded()
                if (subChildren != otherSubChildren) return false
            }

            return true
        }

        override fun treeRepresentation(builder: StringBuilder, level: Int) {
            for (child in children) {
                when (child) {
                    is ParentEventNode -> child.treeRepresentation(builder, level + 2)
                    is SingleEventNode -> child.treeRepresentation(builder, level)
                }
            }
        }
    }
}
