package com.arcana.core.domain.repository

import com.arcana.core.domain.model.Spread
import kotlinx.coroutines.flow.Flow

interface SpreadRepository {
    /**
     * All spreads (bundled + user-authored), respecting the user-defined
     * order from Settings. Spreads not yet in the order list (newly bundled
     * or freshly created custom) are appended in their default order.
     */
    fun observeAllSpreads(): Flow<List<Spread>>

    suspend fun getAllSpreads(): List<Spread>

    suspend fun getSpreadById(id: String): Spread?

    /** Whether a given spread is user-authored (and therefore editable / deletable). */
    suspend fun isCustomSpread(id: String): Boolean

    /**
     * Persist a user-authored spread. New IDs add a row; existing IDs replace.
     * The spread's `layout` is forced to [com.arcana.core.domain.model.SpreadLayout.CUSTOM].
     */
    suspend fun saveCustomSpread(spread: Spread)

    /** Delete a user-authored spread. No-op for bundled IDs. */
    suspend fun deleteCustomSpread(id: String)
}
