package ru.aolenev

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.ktor.client.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.http.*
import io.ktor.serialization.jackson.*
import org.flywaydb.core.Flyway
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.DatabaseConfig
import org.kodein.di.DI
import org.kodein.di.bind
import org.kodein.di.instance
import org.kodein.di.singleton
import ru.aolenev.services.ClaudeService
import ru.aolenev.services.GptService
import ru.aolenev.services.McpService
import ru.aolenev.services.OpenAIService
import ru.aolenev.services.YandexGptService
import javax.sql.DataSource

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
    bind<DataSource>() with singleton {
        val cfg: Config = instance()
        fun cfg(s: String) = cfg.getString(s)
        val hikariConfig = HikariConfig()
        val host = cfg("ai-challenge.db.host")
        val port = cfg("ai-challenge.db.port")
        val dbname = cfg("ai-challenge.db.name")
        hikariConfig.jdbcUrl = "jdbc:postgresql://$host:$port/$dbname"
        hikariConfig.username = cfg("ai-challenge.db.username")
        hikariConfig.password = cfg("ai-challenge.db.password")
        HikariDataSource(hikariConfig)
    }
    bind<Database>() with singleton {
        Database.connect(
            datasource = instance(),
            databaseConfig = DatabaseConfig.invoke { useNestedTransactions = true })
    }
    bind<Flyway>() with singleton { Flyway.configure().dataSource(instance()).load() }
    bind<Config>() with singleton { ConfigFactory.load() }
}

val serviceModule = DI.Module("service") {
    bind<ClaudeService>() with singleton { ClaudeService() }
    bind<GptService>(tag = "yandex") with singleton { YandexGptService() }
    bind<GptService>(tag = "openai") with singleton { OpenAIService() }
    bind<McpService>() with singleton { McpService() }
}

val context =
    DI {
        import(baseModule)
        import(serviceModule)
    }