package com.arcana.core.common

sealed interface ArcanaResult<out T> {
    data class Success<T>(val data: T) : ArcanaResult<T>
    data class Error(val message: String, val cause: Throwable? = null) : ArcanaResult<Nothing>
    data object Loading : ArcanaResult<Nothing>
}

inline fun <T> arcanaTry(block: () -> T): ArcanaResult<T> = try {
    ArcanaResult.Success(block())
} catch (t: Throwable) {
    ArcanaResult.Error(t.message ?: "Unknown error", t)
}
