package dev.wordloom.data

import java.time.LocalDate

/**
 * A fixed list so every device shows the same word on the same day without a
 * server. Order is arbitrary; [pick] walks it by day-of-year.
 */
object DailyWord {

    private val words = listOf(
        "serendipity", "ephemeral", "ubiquitous", "resilient", "eloquent",
        "meticulous", "candid", "tenacious", "lucid", "nuance",
        "paradox", "alleviate", "ambiguous", "benevolent", "coherent",
        "diligent", "empathy", "frugal", "gratitude", "hypothesis",
        "implicit", "integrity", "juxtapose", "keen", "leverage",
        "monotonous", "notion", "obscure", "pragmatic", "quaint",
        "redundant", "scrutiny", "tangible", "unravel", "versatile",
        "wistful", "zealous", "abundant", "brevity", "compelling",
        "deference", "elaborate", "fluctuate", "genuine", "hinder",
        "illuminate", "impartial", "lament", "mindful", "notorious",
        "opaque", "perceive", "quirk", "reconcile", "subtle",
        "threshold", "unfold", "vivid", "withstand", "yield",
    )

    fun pick(date: LocalDate = LocalDate.now()): String =
        words[(date.dayOfYear - 1).mod(words.size)]
}
