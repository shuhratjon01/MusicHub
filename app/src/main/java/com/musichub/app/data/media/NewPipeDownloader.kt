package com.musichub.app.data.media

import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException

const val DEFAULT_USER_AGENT =
    "Mozilla/5.0 (Windows NT 10.0; rv:128.0) Gecko/20100101 Firefox/128.0"

/** OkHttp-backed [Downloader] required by NewPipeExtractor. */
class NewPipeDownloader(private val client: OkHttpClient) : Downloader() {

    override fun execute(request: Request): Response {
        val method = request.httpMethod()
        val url = request.url()
        val data = request.dataToSend()

        val body = data?.toRequestBody()
            ?: if (method == "POST") ByteArray(0).toRequestBody() else null

        val builder = okhttp3.Request.Builder()
            .method(method, body)
            .url(url)
            .header("User-Agent", DEFAULT_USER_AGENT)

        request.headers().forEach { (name, values) ->
            builder.removeHeader(name)
            values.forEach { builder.addHeader(name, it) }
        }

        client.newCall(builder.build()).execute().use { response ->
            if (response.code == 429) throw ReCaptchaException("reCaptcha challenge requested", url)
            return Response(
                response.code,
                response.message,
                response.headers.toMultimap(),
                response.body?.string() ?: "",
                response.request.url.toString(),
            )
        }
    }
}
