package ru.aolenev

import io.ktor.websocket.*
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap

object WsSessionsStorage {
    private val log = LoggerFactory.getLogger(WsSessionsStorage::class.java)
    private val sessions = ConcurrentHashMap<String, WebSocketSession>()

    fun addSession(chatId: String, session: WebSocketSession) {
        sessions[chatId] = session
        log.info("Added websocket session for chatId: $chatId. Total sessions: ${sessions.size}")
    }

    fun removeSession(chatId: String) {
        sessions.remove(chatId)
        log.info("Removed websocket session for chatId: $chatId. Total sessions: ${sessions.size}")
    }

    fun getSession(chatId: String): WebSocketSession? {
        return sessions[chatId]
    }

    fun getAllSessions(): Map<String, WebSocketSession> {
        return sessions.toMap()
    }

    fun getSessionCount(): Int {
        return sessions.size
    }
}