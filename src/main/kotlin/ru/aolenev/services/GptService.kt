package ru.aolenev.services

import ru.aolenev.ResponseWithUsageDetails
import ru.aolenev.SinglePrompt

interface GptService {
    suspend fun singlePrompt(req: SinglePrompt): ResponseWithUsageDetails?
}