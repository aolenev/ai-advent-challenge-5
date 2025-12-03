package ru.aolenev

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import io.ktor.client.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.http.*
import io.ktor.serialization.jackson.*
import org.kodein.di.DI
import org.kodein.di.bind
import org.kodein.di.singleton

val baseModule = DI.Module("base") {
    bind<ObjectMapper>() with singleton {
        ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
    }
    bind<HttpClient>() with singleton {
        HttpClient(OkHttp) {
            install(Logging) {
                logger = Logger.DEFAULT
                level = LogLevel.ALL
                sanitizeHeader { header -> header == HttpHeaders.Authorization }
            }

            install(ContentNegotiation) {
                jackson {
                    disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                    disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                }
            }

            install(HttpTimeout) {
                this.requestTimeoutMillis = 35000
                this.connectTimeoutMillis = 35000
                this.socketTimeoutMillis = 35000
            }

            defaultRequest {
                contentType(ContentType.Application.Json)
            }
        }
    }
}

val serviceModule = DI.Module("service") {
    bind<ClaudeService>() with singleton { ClaudeService() }
}

val context =
    DI {
        import(baseModule)
        import(serviceModule)
    }