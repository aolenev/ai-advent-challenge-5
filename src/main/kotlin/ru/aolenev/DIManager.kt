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
import ru.aolenev.services.ClaudeService
import ru.aolenev.services.GptService
import ru.aolenev.services.OpenAIService
import ru.aolenev.services.YandexGptService

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
                this.requestTimeoutMillis = 60000
                this.connectTimeoutMillis = 60000
                this.socketTimeoutMillis = 60000
            }

            defaultRequest {
                contentType(ContentType.Application.Json)
            }
        }
    }
}

val serviceModule = DI.Module("service") {
    bind<ClaudeService>() with singleton { ClaudeService() }
    bind<GptService>(tag = "yandex") with singleton { YandexGptService() }
    bind<GptService>(tag = "openai") with singleton { OpenAIService() }
}

val context =
    DI {
        import(baseModule)
        import(serviceModule)
    }