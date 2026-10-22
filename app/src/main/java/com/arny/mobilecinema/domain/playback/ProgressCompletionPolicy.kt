package com.arny.mobilecinema.domain.playback

/**
 * Политика завершения и сохранения прогресса (ТЗ §7.3).
 *
 * Одного порога ">= 90%" недостаточно: короткий эпизод считался бы завершённым до титров,
 * а длинный фильм — слишком поздно. Порог: `position >= duration - max(minRemaining, 5%)`.
 * Если источник отдал метку титров, используется она.
 */
class ProgressCompletionPolicy(
    private val minRemainingMs: Long = 30_000L,
    private val completionFraction: Double = 0.95,
    private val minPositionToPersistMs: Long = 10_000L,
    private val creditsStartMs: Long? = null,
) {
    init {
        require(completionFraction in 0.0..1.0) { "completionFraction must be in 0..1" }
        require(minRemainingMs >= 0L) { "minRemainingMs must be >= 0" }
    }

    fun isCompleted(positionMs: Long, durationMs: Long?): Boolean {
        if (positionMs <= 0L) return false
        val duration = durationMs?.takeIf { it > 0L } ?: return false
        val position = positionMs.coerceIn(0L, duration)
        val credits = creditsStartMs?.takeIf { it in 1 until duration }
        if (credits != null) return position >= credits
        val remaining = duration - position
        val allowedRemaining = maxOf(minRemainingMs, (duration * (1.0 - completionFraction)).toLong())
        return remaining <= allowedRemaining
    }

    fun shouldPersist(positionMs: Long, durationMs: Long?): Boolean {
        if (positionMs < minPositionToPersistMs) return false
        val duration = durationMs?.takeIf { it > 0L }
        if (duration != null && positionMs >= duration) return false
        return !isCompleted(positionMs, duration)
    }

    /**
     * Позиция для возобновления. Завершённое видео начинается с нуля; позиция всегда
     * ограничена длительностью (после смены версии потока она может оказаться невалидной).
     */
    fun resumePosition(positionMs: Long, durationMs: Long?): Long {
        if (isCompleted(positionMs, durationMs)) return 0L
        val duration = durationMs?.takeIf { it > 0L } ?: return positionMs.coerceAtLeast(0L)
        return positionMs.coerceIn(0L, duration)
    }
}
