package com.arny.mobilecinema.presentation.details

/**
 * Защита от двойного клика по «Смотреть» (план v3 §4.3).
 *
 * Повторный клик в пределах окна игнорируется: иначе один пользовательский жест открывает
 * два экземпляра плеера, а второй запуск перебивает первый. Логика без Android — проверяется
 * юнит-тестами, время подставляется снаружи.
 */
class PlayClickGate(private val windowMillis: Long = DEFAULT_WINDOW_MILLIS) {

    private var lastAcceptedAt: Long = NEVER

    /** `true`, если клик можно обрабатывать; время последнего принятого клика запоминается. */
    fun tryAccept(now: Long): Boolean {
        val elapsed = now - lastAcceptedAt
        if (lastAcceptedAt != NEVER && elapsed in 0 until windowMillis) return false
        lastAcceptedAt = now
        return true
    }

    fun reset() {
        lastAcceptedAt = NEVER
    }

    companion object {
        const val DEFAULT_WINDOW_MILLIS = 600L

        private const val NEVER = Long.MIN_VALUE
    }
}
