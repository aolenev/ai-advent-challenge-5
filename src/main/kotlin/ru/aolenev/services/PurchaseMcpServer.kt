package ru.aolenev.services

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.ObjectMapper
import org.kodein.di.instance
import org.slf4j.LoggerFactory
import ru.aolenev.context
import ru.aolenev.model.McpTool
import ru.aolenev.model.McpToolsParams
import ru.aolenev.model.McpToolsResponse
import ru.aolenev.model.McpToolsResult
import ru.aolenev.repo.PurchaseTable

data class UpdatePurchaseCategoryInput(
    @JsonProperty("id") val id: Long,
    @JsonProperty("category") val category: String
)

data class PurchaseOutput(
    @JsonProperty("id") val id: Long,
    @JsonProperty("purchase_info") val purchaseInfo: String
)

data class GetAllPurchasesWithEmptyCategoryOutput(
    @JsonProperty("purchases") val purchases: List<PurchaseOutput>
)

data class PurchaseWithCategoryOutput(
    @JsonProperty("id") val id: Long,
    @JsonProperty("purchase_info") val purchaseInfo: String,
    @JsonProperty("category") val category: String
)

data class GetAllPurchasesWithCategoryOutput(
    @JsonProperty("purchases") val purchases: List<PurchaseWithCategoryOutput>
)

data class UpdateCategoryOutput(
    @JsonProperty("success") val success: Boolean,
    @JsonProperty("message") val message: String
)

class PurchaseMcpServer {
    private val log by lazy { LoggerFactory.getLogger(this.javaClass.name) }
    private val mapper: ObjectMapper by context.instance()

    fun listTools(): McpToolsResponse {
        return try {
            log.info("Loading purchase tools")

            val getAllPurchasesJson = this::class.java
                .getResource("/tool-templates/get_all_purchases_with_empty_category.json")!!
                .readText()
            val getAllPurchasesTool = mapper.readValue(getAllPurchasesJson, McpTool::class.java)

            val updateCategoryJson = this::class.java
                .getResource("/tool-templates/update_purchase_category.json")!!
                .readText()
            val updateCategoryTool = mapper.readValue(updateCategoryJson, McpTool::class.java)

            val getAllWithCategoryJson = this::class.java
                .getResource("/tool-templates/get_all_purchases_with_category.json")!!
                .readText()
            val getAllWithCategoryTool = mapper.readValue(getAllWithCategoryJson, McpTool::class.java)

            log.info("Successfully loaded tools: ${getAllPurchasesTool.name}, ${updateCategoryTool.name}, ${getAllWithCategoryTool.name}")

            McpToolsResponse(
                jsonrpc = "2.0",
                id = 2,
                result = McpToolsResult(tools = listOf(getAllPurchasesTool, updateCategoryTool, getAllWithCategoryTool), isError = false)
            )
        } catch (e: Exception) {
            log.error("Error loading tools", e)
            throw e
        }
    }

    fun callTool(params: McpToolsParams): McpToolsResponse {
        return try {
            when (params.name) {
                "get_all_purchases_with_empty_category" -> {
                    log.info("Calling get_all_purchases_with_empty_category tool")

                    val purchases = PurchaseTable.findAllWithObjectInfo()
                    val output = GetAllPurchasesWithEmptyCategoryOutput(
                        purchases = purchases.map {
                            PurchaseOutput(id = it.id, purchaseInfo = it.objectInfo)
                        }
                    )

                    log.info("Found ${purchases.size} purchases with object_info")

                    McpToolsResponse(
                        jsonrpc = "2.0",
                        id = 2,
                        result = McpToolsResult(
                            content = output,
                            isError = false
                        )
                    )
                }
                "update_purchase_category" -> {
                    log.info("Calling update_purchase_category tool with arguments: ${params.arguments}")

                    val input = mapper.convertValue(
                        params.arguments,
                        UpdatePurchaseCategoryInput::class.java
                    )

                    val success = PurchaseTable.updateCategory(input.id, input.category)

                    val output = UpdateCategoryOutput(
                        success = success,
                        message = if (success) "Category updated successfully" else "Purchase not found"
                    )

                    log.info("Update category result: success=$success, id=${input.id}, category=${input.category}")

                    McpToolsResponse(
                        jsonrpc = "2.0",
                        id = 2,
                        result = McpToolsResult(
                            content = output,
                            isError = false
                        )
                    )
                }
                "get_all_purchases_with_category" -> {
                    log.info("Calling get_all_purchases_with_category tool")

                    val purchases = PurchaseTable.findAllWithCategory()
                    val output = GetAllPurchasesWithCategoryOutput(
                        purchases = purchases.map {
                            PurchaseWithCategoryOutput(
                                id = it.id,
                                purchaseInfo = it.objectInfo,
                                category = it.category
                            )
                        }
                    )

                    log.info("Found ${purchases.size} purchases with category")

                    McpToolsResponse(
                        jsonrpc = "2.0",
                        id = 2,
                        result = McpToolsResult(
                            content = output,
                            isError = false
                        )
                    )
                }
                else -> {
                    log.warn("Unknown tool name: ${params.name}")
                    McpToolsResponse(
                        jsonrpc = "2.0",
                        id = 2,
                        result = McpToolsResult(
                            content = mapOf("error" to "Unknown tool: ${params.name}"),
                            isError = true
                        )
                    )
                }
            }
        } catch (e: Exception) {
            log.error("Error calling tool", e)
            McpToolsResponse(
                jsonrpc = "2.0",
                id = 2,
                result = McpToolsResult(
                    content = mapOf("error" to e.message),
                    isError = true
                )
            )
        }
    }
}
