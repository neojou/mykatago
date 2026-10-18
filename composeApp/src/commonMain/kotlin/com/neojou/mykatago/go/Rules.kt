package com.neojou.mykatago.go

enum class Ruleset(
    val katago: String,
    val label: String,
    val allowsMultiSuicide: Boolean,
) {
    JAPANESE("japanese", "日本規則", false),
    CHINESE("chinese", "中國規則", false),
    KOREAN("korean", "韓國規則", false),
    AGA("aga", "AGA", false),
    TROMP_TAYLOR("tromp-taylor", "Tromp-Taylor", true),
    NEW_ZEALAND("new zealand", "紐西蘭規則", true),
    STONE_SCORING("stone_scoring", "數子", false),
}

fun rulesetFromRu(ru: String?): Ruleset {
    val text = ru?.trim()?.lowercase().orEmpty()
    if (text.isEmpty()) return Ruleset.JAPANESE
    return when {
        text.startsWith("jp") || text.contains("japanese") || text.contains("日本") -> Ruleset.JAPANESE
        text.startsWith("cn") || text.contains("chinese") || text.contains("中國") -> Ruleset.CHINESE
        text.startsWith("kr") || text.contains("korean") || text.contains("韓國") -> Ruleset.KOREAN
        text.contains("aga") -> Ruleset.AGA
        text == "nz" || (text.contains("new") && text.contains("zealand")) -> Ruleset.NEW_ZEALAND
        text == "tt" || text.contains("tromp") -> Ruleset.TROMP_TAYLOR
        text.contains("stone") -> Ruleset.STONE_SCORING
        else -> Ruleset.JAPANESE
    }
}

/** Used when the SGF has no KM property. */
fun defaultKomi(rules: Ruleset): Double = when (rules) {
    Ruleset.CHINESE, Ruleset.AGA, Ruleset.TROMP_TAYLOR, Ruleset.STONE_SCORING -> 7.5
    else -> 6.5
}

/**
 * Standard handicap points, black stones, for 9, 13, and 19.
 * Returns an empty list when [count] is below 2 or the size has no table.
 */
fun standardHandicap(size: Int, count: Int): List<Point> {
    if (count < 2) return emptyList()
    return handicapTable(size)[count].orEmpty()
}

private fun handicapTable(size: Int): Map<Int, List<Point>> {
    val (left, right, center) = when (size) {
        19 -> Triple(3, 15, 9)
        13 -> Triple(3, 9, 6)
        9 -> Triple(2, 6, 4)
        else -> return emptyMap()
    }
    val topRight = Point(right, left)
    val bottomLeft = Point(left, right)
    val bottomRight = Point(right, right)
    val topLeft = Point(left, left)
    val tengen = Point(center, center)
    val leftSide = Point(left, center)
    val rightSide = Point(right, center)
    val topSide = Point(center, left)
    val bottomSide = Point(center, right)
    val corners = listOf(topRight, bottomLeft, bottomRight, topLeft)
    return mapOf(
        2 to listOf(topRight, bottomLeft),
        3 to listOf(topRight, bottomLeft, topLeft),
        4 to corners,
        5 to corners + tengen,
        6 to corners + listOf(leftSide, rightSide),
        7 to corners + listOf(leftSide, rightSide, tengen),
        8 to corners + listOf(leftSide, rightSide, topSide, bottomSide),
        9 to corners + listOf(leftSide, rightSide, topSide, bottomSide, tengen),
    )
}
