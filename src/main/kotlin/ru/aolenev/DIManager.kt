package ru.aolenev

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import org.kodein.di.DI
import org.kodein.di.bind
import org.kodein.di.singleton

val baseModule = DI.Module("base") {
    bind<ObjectMapper>() with singleton {
        ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
    }
}

val serviceModule = DI.Module("service") {
    bind<ClaudeClient>() with singleton { ClaudeClient() }
}

val context =
    DI {
        import(baseModule)
        import(serviceModule)
    }