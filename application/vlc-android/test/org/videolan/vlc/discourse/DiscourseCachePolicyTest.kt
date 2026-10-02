package org.videolan.vlc.discourse

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import okhttp3.Interceptor
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import java.io.IOException

class DiscourseCachePolicyTest {
    @Test
    fun appliesGetOnlyPolicyAndFallsBackToStaleCache() {
        assertEquals(FORCE_REFRESH, cacheControl(true))
        assertEquals(null, cacheControl(false))
        val get = Request.Builder().url("https://example.test/discourses?page=1")
            .header("Cache-Control", FORCE_REFRESH).build()
        val put = get.newBuilder().method("PUT", okhttp3.RequestBody.create(null, byteArrayOf())).build()
        val responseChain = mockk<Interceptor.Chain>()
        every { responseChain.request() } returns get
        every { responseChain.proceed(get) } returns response(get)
        assertEquals("public, max-age=86400", CacheResponseInterceptor().intercept(responseChain).header("Cache-Control"))

        every { responseChain.request() } returns put
        every { responseChain.proceed(put) } returns response(put)
        assertEquals(null, CacheResponseInterceptor().intercept(responseChain).header("Cache-Control"))

        val stale = response(get)
        val fallbackChain = mockk<Interceptor.Chain>()
        val fallbackRequest = slot<Request>()
        every { fallbackChain.request() } returns get
        every { fallbackChain.proceed(get) } throws IOException("offline")
        every { fallbackChain.proceed(capture(fallbackRequest)) } returns stale
        assertSame(stale, StaleCacheInterceptor().intercept(fallbackChain))
        assertEquals("only-if-cached, max-stale=2147483647", fallbackRequest.captured.header("Cache-Control"))
    }

    private fun response(request: Request) = Response.Builder()
        .request(request).protocol(Protocol.HTTP_1_1).code(200).message("OK").build()
}
