import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import com.typesafe.config.ConfigFactory
import org.flywaydb.core.Flyway
import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.json.jsonb
import org.kodein.di.instance
import ru.aolenev.context

private val mapper: ObjectMapper by context.instance()

fun Table.jsonb(name: String): Column<ObjectNode> {
    return jsonb(name, mapper::writeValueAsString)
    { mapper.readValue(it, ObjectNode::class.java) }
}

fun <R> R.toObjectNode(): ObjectNode {
    if (this is String && this.isEmpty()) return mapper.createObjectNode()
    return mapper.convertValue(this, ObjectNode::class.java)
}

fun dbMigration() {
    val config = ConfigFactory.parseResources("flyway.conf")
    val locations = config.getString("flyway.locations").split(",")
    Flyway.configure()
        .dataSource(
            config.getString("flyway.url"),
            config.getString("flyway.user"),
            config.getString("flyway.password")
        )
        .locations(*locations.toTypedArray()).load()
        .migrate()
}