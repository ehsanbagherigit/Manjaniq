package com.manjaniq.core

import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketAddress
import java.net.SocketTimeoutException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Checks whether the SOCKS5 server on a loopback port answers its greeting, asked over at most
 * one connection per port at a time.
 *
 * A greeting that got no answer yet is kept waiting, and the next question about that port reads
 * on, on the same connection, while it stays unanswered; questions asked at the same time share
 * one answer.
 */
internal class SocksAnswers(
    private val waitMs: Int,
    private val connectTimeoutMs: Int = waitMs,
) {
    private class Question {
        val done = CountDownLatch(1)

        @Volatile
        var answered = false
    }

    private enum class Reply { ANSWERED, WAITING, ENDED }

    private val lock = Any()
    private val asking = HashMap<Int, Question>()
    private val waiting = HashMap<Int, Socket>()

    fun answers(port: Int): Boolean {
        var mine = false
        val question = synchronized(lock) {
            asking[port] ?: Question().also {
                asking[port] = it
                mine = true
            }
        }
        if (!mine) {
            val longest = connectTimeoutMs + 4L * waitMs + SHARED_WAIT_SLACK_MS
            return try {
                question.done.await(longest, TimeUnit.MILLISECONDS) && question.answered
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
                false
            }
        }
        try {
            question.answered = ask(port)
        } finally {
            synchronized(lock) { asking.remove(port) }
            question.done.countDown()
        }
        return question.answered
    }

    private fun ask(port: Int): Boolean {
        val kept = synchronized(lock) { waiting.remove(port) }
        if (kept != null && read(kept) == Reply.WAITING) return keep(port, kept)
        val socket = greet(port) ?: return false
        return when (read(socket)) {
            Reply.ANSWERED -> true
            Reply.WAITING -> keep(port, socket)
            Reply.ENDED -> false
        }
    }

    private fun greet(port: Int): Socket? {
        val socket = Socket()
        return try {
            socket.connect(InetSocketAddress(AetherCoreManager.LOOPBACK, port), connectTimeoutMs)
            if (isSelfConnect(socket.localSocketAddress, socket.remoteSocketAddress)) {
                closeQuietly(socket)
                return null
            }
            socket.getOutputStream().write(GREETING)
            socket
        } catch (_: IOException) {
            closeQuietly(socket)
            null
        }
    }

    private fun read(socket: Socket): Reply {
        val version = try {
            socket.soTimeout = waitMs
            socket.getInputStream().read()
        } catch (_: SocketTimeoutException) {
            return Reply.WAITING
        } catch (_: IOException) {
            -1
        }
        val reply = try {
            if (version == SOCKS_VERSION && socket.getInputStream().read() in ANSWERED_METHODS) Reply.ANSWERED else Reply.ENDED
        } catch (_: IOException) {
            Reply.ENDED
        }
        closeQuietly(socket)
        return reply
    }

    private fun keep(port: Int, socket: Socket): Boolean {
        synchronized(lock) { waiting.put(port, socket) }?.let(::closeQuietly)
        return false
    }

    fun close(port: Int) {
        synchronized(lock) { waiting.remove(port) }?.let(::closeQuietly)
    }

    fun closeAll() {
        val sockets = synchronized(lock) {
            waiting.values.toList().also { waiting.clear() }
        }
        sockets.forEach(::closeQuietly)
    }

    private fun closeQuietly(socket: Socket) {
        try {
            socket.close()
        } catch (_: IOException) {
        }
    }

    internal companion object {
        private val GREETING = byteArrayOf(5, 1, 0)
        private const val SOCKS_VERSION = 5
        private val ANSWERED_METHODS = setOf(0x00, 0xFF)
        private const val SHARED_WAIT_SLACK_MS = 500L

        fun isSelfConnect(local: SocketAddress?, remote: SocketAddress?): Boolean = local != null && local == remote
    }
}
