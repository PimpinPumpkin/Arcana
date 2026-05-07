package com.arcana.app.navigation

object Routes {
    const val MAIN = "main"

    const val LIBRARY = "library"
    const val SPREADS = "spreads"
    const val JOURNAL = "journal"
    const val SETTINGS = "settings"

    const val CARD_DETAIL = "cardDetail"
    fun cardDetail(cardId: String) = "$CARD_DETAIL/$cardId"
    const val CARD_DETAIL_PATTERN = "$CARD_DETAIL/{cardId}"
    const val ARG_CARD_ID = "cardId"

    const val READING_FLOW = "readingFlow"
    fun readingFlow(spreadId: String) = "$READING_FLOW/$spreadId"
    const val READING_FLOW_PATTERN = "$READING_FLOW/{spreadId}"
    const val ARG_SPREAD_ID = "spreadId"

    const val SPREAD_OVERVIEW = "spreadOverview"
    fun spreadOverview(spreadId: String) = "$SPREAD_OVERVIEW/$spreadId"
    const val SPREAD_OVERVIEW_PATTERN = "$SPREAD_OVERVIEW/{spreadId}"

    const val LOG_PHYSICAL_PICKER = "logPhysicalPicker"
    const val LOG_PHYSICAL = "logPhysical"
    fun logPhysical(spreadId: String) = "$LOG_PHYSICAL/$spreadId"
    const val LOG_PHYSICAL_PATTERN = "$LOG_PHYSICAL/{spreadId}"

    const val JOURNAL_DETAIL = "journalDetail"
    fun journalDetail(readingId: String) = "$JOURNAL_DETAIL/$readingId"
    const val JOURNAL_DETAIL_PATTERN = "$JOURNAL_DETAIL/{readingId}"
    const val ARG_READING_ID = "readingId"

    /**
     * Custom-spread editor — two routes so creating and editing have
     * distinct nav destinations (spreadId is required for edit).
     */
    const val CUSTOM_SPREAD_NEW = "customSpreadNew"
    const val CUSTOM_SPREAD_EDIT = "customSpreadEdit"
    fun customSpreadEdit(spreadId: String) = "$CUSTOM_SPREAD_EDIT/$spreadId"
    const val CUSTOM_SPREAD_EDIT_PATTERN = "$CUSTOM_SPREAD_EDIT/{spreadId}"

    /** Manage decks (list + import + delete). Reachable from Settings → Manage decks. */
    const val MANAGE_DECKS = "manageDecks"

    /** Per-card editor for one custom deck. spreadId-style required arg. */
    const val EDIT_DECK = "editDeck"
    fun editDeck(deckId: String) = "$EDIT_DECK/$deckId"
    const val EDIT_DECK_PATTERN = "$EDIT_DECK/{deckId}"
    const val ARG_DECK_ID = "deckId"

    /** Backup & restore (export / import custom spreads + saved readings). */
    const val BACKUP = "backup"
}
