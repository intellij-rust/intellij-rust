/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.update

import com.intellij.ide.plugins.PluginManager
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.PermanentInstallationID
import com.intellij.openapi.application.ex.ApplicationInfoEx
import com.intellij.openapi.components.ApplicationComponent
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.event.EditorFactoryAdapter
import com.intellij.openapi.editor.event.EditorFactoryEvent
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.util.JDOMUtil
import com.intellij.openapi.util.SystemInfo
import com.intellij.util.io.HttpRequests
import org.jdom.JDOMException
import org.rust.lang.core.psi.isRustFile
import org.rust.openapiext.isUnitTestMode
import java.io.IOException
import java.net.URLEncoder
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit

class UpdateComponent : ApplicationComponent, Disposable {
    override fun getComponentName(): String = javaClass.name

    override fun initComponent() {
        if (!isUnitTestMode) {
            EditorFactory.getInstance().addEditorFactoryListener(EDITOR_LISTENER, this)
        }
    }

    override fun disposeComponent() {
        // NOP
    }

    override fun dispose() = disposeComponent()


    object EDITOR_LISTENER : EditorFactoryAdapter() {
        override fun editorCreated(event: EditorFactoryEvent) {
            val document = event.editor.document
            val file = FileDocumentManager.getInstance().getFile(document)
            if (file != null && file.isRustFile) {
                update()
            }
        }
    }

    companion object {
        private val LAST_UPDATE: String = "org.rust.LAST_UPDATE"
        private val PLUGIN_ID: String = "org.rust.lang"

        private val LOG = Logger.getInstance(UpdateComponent::class.java)

        fun update() {
            val properties = PropertiesComponent.getInstance()
            val lastUpdate = properties.getOrInitLong(LAST_UPDATE, 0L)
            val shouldUpdate = lastUpdate == 0L || System.currentTimeMillis() - lastUpdate > TimeUnit.DAYS.toMillis(1)
            if (shouldUpdate) {
                properties.setValue(LAST_UPDATE, System.currentTimeMillis().toString())
                val url = updateUrl
                ApplicationManager.getApplication().executeOnPooledThread {
                    try {
                        HttpRequests.request(url).connect {
                            try {
                                JDOMUtil.load(it.reader)
                            } catch (e: JDOMException) {
                                LOG.warn(e)
                            }
                            LOG.info("updated: $url")
                        }
                    } catch (ignored: UnknownHostException) {
                        // No internet connections, no need to log anything
                    } catch (e: IOException) {
                        LOG.warn(e)
                    }
                }
            }
        }

        private val updateUrl: String get() {
            val applicationInfo = ApplicationInfoEx.getInstanceEx()
            val buildNumber = applicationInfo.build.asString()
            val plugin = PluginManager.getPlugin(PluginId.getId(PLUGIN_ID))!!
            val pluginId = plugin.pluginId.idString
            val os = URLEncoder.encode("${SystemInfo.OS_NAME} ${SystemInfo.OS_VERSION}", Charsets.UTF_8.name())
            val uid = PermanentInstallationID.get()
            val baseUrl = "https://plugins.jetbrains.com/plugins/list"
            return "$baseUrl?pluginId=$pluginId&build=$buildNumber&pluginVersion=${plugin.version}&os=$os&uuid=$uid"
        }
    }
}

