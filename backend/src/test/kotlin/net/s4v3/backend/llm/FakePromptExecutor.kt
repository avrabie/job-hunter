package net.s4v3.backend.llm

import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.prompt.Prompt
import ai.koog.prompt.dsl.ModerationResult
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.ResponseMetaInfo
import ai.koog.prompt.streaming.StreamFrame
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

/**
 * A [PromptExecutor] that never calls anyone: it answers the n-th request with the n-th canned text (the last one repeats)
 * and keeps every prompt it was given, so tests can check what would have been sent to the model.
 */
class FakePromptExecutor(private vararg val answers: String) : PromptExecutor() {
    val prompts = mutableListOf<Prompt>()

    /** The model of every request, in order. */
    val models = mutableListOf<LLModel>()

    override suspend fun execute(prompt: Prompt, model: LLModel, tools: List<ToolDescriptor>): Message.Assistant {
        prompts += prompt
        models += model
        return Message.Assistant(answers[minOf(prompts.size - 1, answers.lastIndex)], ResponseMetaInfo.Empty)
    }

    override fun executeStreaming(prompt: Prompt, model: LLModel, tools: List<ToolDescriptor>): Flow<StreamFrame> = emptyFlow()

    override suspend fun moderate(prompt: Prompt, model: LLModel): ModerationResult = error("moderation is not used")

    override fun close() {}

    /** All text sent to the model, system and user messages together. */
    fun sentText(): String = prompts.flatMap { it.messages }.joinToString("\n") { it.textContent() }
}
