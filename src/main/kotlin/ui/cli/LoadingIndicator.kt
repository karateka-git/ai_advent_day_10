package ui.cli

import java.util.ArrayDeque
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Общий консольный спиннер с поддержкой вложенных статусных сообщений.
 */
class LoadingIndicator {
    private val running = AtomicBoolean(false)
    private val messages = ArrayDeque<String>()
    private var thread: Thread? = null

    /**
     * Добавляет новое статусное сообщение в стек индикатора.
     */
    @Synchronized
    fun start(message: String) {
        messages.addLast(message)
        ensureThreadStarted()
    }

    /**
     * Удаляет последнее статусное сообщение и останавливает спиннер, когда стек становится пустым.
     */
    @Synchronized
    fun stop() {
        if (messages.isNotEmpty()) {
            messages.removeLast()
        }

        if (messages.isEmpty()) {
            stopThread()
        }
    }

    @Synchronized
    private fun ensureThreadStarted() {
        if (running.get()) {
            return
        }

        running.set(true)
        thread = Thread {
            var step = 0
            while (running.get()) {
                val message = synchronized(this) {
                    messages.lastOrNull()
                } ?: break
                val dots = ".".repeat(step % 4)
                val padding = " ".repeat(3 - dots.length)
                print("\r$message$dots$padding")
                Thread.sleep(350)
                step++
            }
        }.apply {
            isDaemon = true
            start()
        }
    }

    @Synchronized
    private fun stopThread() {
        if (!running.getAndSet(false)) {
            return
        }

        thread?.join(500)
        thread = null
        print("\r${" ".repeat(60)}\r")
    }
}
