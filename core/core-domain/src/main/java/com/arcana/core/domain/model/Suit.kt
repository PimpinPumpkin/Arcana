package com.arcana.core.domain.model

enum class Suit(
    val displayName: String,
    val element: Element,
    val keyword: String,
) {
    WANDS("Wands", Element.FIRE, "Passion, action, creativity"),
    CUPS("Cups", Element.WATER, "Emotion, intuition, relationships"),
    SWORDS("Swords", Element.AIR, "Intellect, conflict, communication"),
    PENTACLES("Pentacles", Element.EARTH, "Material, body, work"),
}

enum class Element(val displayName: String) {
    FIRE("Fire"),
    WATER("Water"),
    AIR("Air"),
    EARTH("Earth"),
    SPIRIT("Spirit"),
}
