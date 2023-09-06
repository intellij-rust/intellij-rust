/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust

import com.intellij.testFramework.fixtures.impl.BaseFixture
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.Assert
import org.rust.ide.utils.USER_AGENT

typealias ResponseHandler = (RecordedRequest) -> MockResponse?

class MockServerFixture : BaseFixture() {

    private var handler: ResponseHandler? = null

    private val mockWebServer = MockWebServer().apply {
        dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                Assert.assertEquals(USER_AGENT, request.getHeader("User-Agent"))
                return handler?.invoke(request) ?: MockResponse().setResponseCode(404)
            }
        }
    }

    val baseUrl: String get() = mockWebServer.url("/").toString()

    fun withHandler(handler: ResponseHandler) {
        this.handler = handler
    }

    override fun tearDown() {
        mockWebServer.shutdown()
        super.tearDown()
    }
}
