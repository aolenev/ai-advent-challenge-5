package ru.aolenev.services

import ru.aolenev.model.ResponseWithUsageDetails
import ru.aolenev.model.SinglePrompt

interface GptService {
    suspend fun singlePrompt(req: SinglePrompt): ResponseWithUsageDetails?
}