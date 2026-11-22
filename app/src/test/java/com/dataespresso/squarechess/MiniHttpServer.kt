package com.dataespresso.squarechess

import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.SocketException
import kotlin.concurrent.thread

/**
 * A very small HTTP/1.1 server on 127.0.0.1 for JVM tests: one request per connection, then
 * close. The JDK's own HttpServer is not on the Android unit-test classpath.
 */
class MiniHttpServer(private val handler: (Request) -> Response) : AutoCloseable {
    data class Request(val method: String, val path: String, val headers: Map<String, String>, val body: String) {
        fun header(name: String): String? = headers[name.lowercase()]
    }
    data class Response(val status: Int, val body: String, val type: String = "application/json")

    private val socket = ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"))
    val port: Int get() = socket.localPort
    val url: String get() = "http://127.0.0.1:$port"

    init {
        thread(isDaemon = true, name = "mini-http") {
            while (!socket.isClosed) {
                val client = try { socket.accept() } catch (_: SocketException) { break }
                client.use { c ->
                    runCatching {
                        val input = BufferedInputStream(c.getInputStream())
                        val requestLine = readLine(input) ?: return@runCatching
                        val headers = mutableMapOf<String, String>()
                        while (true) {
                            val line = readLine(input) ?: break
                            if (line.isEmpty()) break
                            headers[line.substringBefore(':').trim().lowercase()] = line.substringAfter(':').trim()
                        }
                        val length = headers["content-length"]?.toIntOrNull() ?: 0
                        val body = ByteArray(length).also { var read = 0; while (read < length) { val n = input.read(it, read, length - read); if (n < 0) break; read += n } }
                        val (method, target) = requestLine.split(' ').let { it[0] to it[1] }
                        val response = handler(Request(method, target.substringBefore('?'), headers, body.toString(Charsets.UTF_8)))
                        val bytes = response.body.toByteArray(Charsets.UTF_8)
                        val head = "HTTP/1.1 ${response.status} X\r\nContent-Type: ${response.type}\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n"
                        c.getOutputStream().apply { write(head.toByteArray()); write(bytes); flush() }
                    }
                }
            }
        }
    }

    private fun readLine(input: InputStream): String? {
        val out = ByteArrayOutputStream()
        while (true) {
            val b = input.read()
            if (b < 0) return if (out.size() == 0) null else out.toString("UTF-8")
            if (b == '\n'.code) return out.toString("UTF-8").trimEnd('\r')
            out.write(b)
        }
    }

    override fun close() = socket.close()
}
