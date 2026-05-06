package com.arcana.service.ai

import com.arcana.core.domain.model.AiBackendType
import com.arcana.service.ai.cloud.ClaudeInterpreter
import com.arcana.service.ai.local.LocalLlmInterpreter
import com.arcana.service.ai.local.RuleBasedInterpreter
import dagger.Binds
import dagger.MapKey
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoMap

@MapKey
annotation class AiBackendKey(val value: AiBackendType)

@Module
@InstallIn(SingletonComponent::class)
abstract class AiModule {

    @Binds
    @IntoMap
    @AiBackendKey(AiBackendType.RULE_BASED)
    abstract fun bindRuleBased(impl: RuleBasedInterpreter): TarotInterpreter

    @Binds
    @IntoMap
    @AiBackendKey(AiBackendType.LOCAL_LLM)
    abstract fun bindLocalLlm(impl: LocalLlmInterpreter): TarotInterpreter

    @Binds
    @IntoMap
    @AiBackendKey(AiBackendType.CLAUDE_API)
    abstract fun bindClaude(impl: ClaudeInterpreter): TarotInterpreter
}
