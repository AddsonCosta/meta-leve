package com.addsoncosta.evolui.health

/**
 * Transforma "de 8 em 8 horas", "2 vezes ao dia", "08:00 e 20:00" ou "ao deitar" em horários do dia,
 * para criar os lembretes de remédios e suplementos. Puro (sem Android), testado na JVM.
 */
object MedicationSchedule {
    /** Primeiro horário do dia quando a receita não diz (8h). */
    const val FIRST_MINUTE = 8 * 60

    /** Dias de lembrete para uso contínuo (depois dá para criar mais pelo mesmo botão). */
    const val CONTINUOUS_DAYS = 30

    /** Limite de dias para não criar avisos demais de uma vez. */
    const val MAX_DAYS = 90

    private val words = mapOf("uma" to 1, "um" to 1, "duas" to 2, "dois" to 2, "tres" to 3, "quatro" to 4, "cinco" to 5, "seis" to 6)

    private val interval = Regex("(?<![a-z0-9])(?:de\\s+(\\d{1,2})\\s+em\\s+\\1\\s*h|(\\d{1,2})\\s*/\\s*\\2\\s*h|a cada\\s+(\\d{1,2})\\s*h)")
    private val perDay = Regex("(?<![a-z0-9])(\\d|uma|um|duas|dois|tres|quatro|cinco|seis)\\s*(?:x|vez|vezes)\\s*(?:ao|por|no|/)?\\s*dia")
    private val clock = Regex("(?<![\\d/,.])([01]?\\d|2[0-3])(?:\\s*:\\s*([0-5]\\d)|\\s*h(?:s|rs)?(?![a-z])\\s*([0-5]\\d)?)(?![\\d/])")
    private val weekly = Regex("(?<![a-z])(semana|semanal|semanais|quinzenal|mensal|por mes|ao mes)(?![a-z])")

    private val moments: List<Pair<Regex, Int>> = listOf(
        "em jejum|ao acordar|ao levantar" to 7 * 60,
        "manha|cafe da manha|desjejum" to 8 * 60,
        "almoco" to 12 * 60,
        "tarde|lanche" to 16 * 60,
        "jantar" to 19 * 60,
        "noite" to 21 * 60,
        "ao deitar|antes de dormir|hora de dormir" to 22 * 60,
    ).map { (k, v) -> Regex("(?<![a-z])(?:$k)(?![a-z])") to v }

    /** Horários (minutos desde a meia-noite) em ordem. Vazio quando não deu para entender. */
    fun times(text: String?): List<Int> {
        if (text.isNullOrBlank()) return emptyList()
        val f = TextScan.fold(text).replace(Regex("\\s+"), " ")
        if (weekly.containsMatchIn(f)) return emptyList()
        interval.find(f)?.let { m ->
            val n = (m.groupValues[1].ifEmpty { m.groupValues[2] }.ifEmpty { m.groupValues[3] }).toIntOrNull()
            if (n != null && n in 1..24) return spread(FIRST_MINUTE, n * 60, 24 / n)
        }
        // Horários escritos ("08:00 e 20:00", "às 22h"), sem confundir com "de 8 em 8 horas".
        val clocks = clock.findAll(f).mapNotNull { m ->
            val h = m.groupValues[1].toInt()
            val min = (m.groupValues[2].ifEmpty { m.groupValues[3] }).ifEmpty { "0" }.toInt()
            h * 60 + min
        }.distinct().sorted().toList()
        if (clocks.isNotEmpty()) return clocks
        perDay.find(f)?.let { m ->
            val n = m.groupValues[1].toIntOrNull() ?: words[m.groupValues[1]] ?: 1
            return perDayTimes(n)
        }
        val named = moments.filter { it.first.containsMatchIn(f) }.map { it.second }
        // "manhã" e "em jejum" juntos ficam num horário só.
        val merged = if (7 * 60 in named && 8 * 60 in named) named - 8 * 60 else named
        // "noite" e "ao deitar" juntos também.
        val merged2 = if (21 * 60 in merged && 22 * 60 in merged) merged - 21 * 60 else merged
        if (merged2.isNotEmpty()) return merged2.distinct().sorted()
        if (Regex("(?<![a-z])(ao dia|por dia|diariamente|todo dia|todos os dias|uma vez|1 vez|diario|diaria)(?![a-z])").containsMatchIn(f)) {
            return listOf(FIRST_MINUTE)
        }
        return emptyList()
    }

    /** Horários espalhados no dia acordado: 1x = 8h; 2x = 8h e 20h; 3x = 8h, 14h e 20h... */
    fun perDayTimes(n: Int): List<Int> = when {
        n <= 1 -> listOf(FIRST_MINUTE)
        n == 2 -> listOf(8 * 60, 20 * 60)
        n == 3 -> listOf(8 * 60, 14 * 60, 20 * 60)
        n == 4 -> listOf(8 * 60, 12 * 60, 16 * 60, 20 * 60)
        else -> spread(FIRST_MINUTE, (24 * 60) / n.coerceAtMost(24), n.coerceAtMost(24))
    }

    private fun spread(start: Int, step: Int, count: Int): List<Int> =
        (0 until count.coerceAtLeast(1)).map { (start + it * step) % (24 * 60) }.distinct().sorted()

    /** Por quantos dias criar os avisos: a duração da receita, ou [CONTINUOUS_DAYS] para uso contínuo / sem data. */
    fun days(durationDays: Int?, continuous: Boolean): Int = when {
        durationDays != null && durationDays > 0 -> durationDays.coerceAtMost(MAX_DAYS)
        continuous -> CONTINUOUS_DAYS
        else -> CONTINUOUS_DAYS
    }

    /** "08:00, 14:00, 20:00". */
    fun format(times: List<Int>): String = times.joinToString(", ") { "%02d:%02d".format(it / 60, it % 60) }

    /** Lê "08:00, 20:00" (ou "8h e 20h") digitado pelo usuário. */
    fun parseTimes(text: String): List<Int> =
        clock.findAll(TextScan.fold(text)).map { m ->
            m.groupValues[1].toInt() * 60 + (m.groupValues[2].ifEmpty { m.groupValues[3] }).ifEmpty { "0" }.toInt()
        }.distinct().sorted().toList()

    /**
     * Dias e horários dos avisos: a partir de [startDay], por [days] dias, pulando horários de hoje
     * que já passaram ([nowMinute]). Devolve pares (epochDay, minuto).
     */
    fun slots(startDay: Long, days: Int, times: List<Int>, todayEpochDay: Long, nowMinute: Int): List<Pair<Long, Int>> {
        val first = maxOf(startDay, todayEpochDay)
        val last = startDay + days - 1
        if (last < first || times.isEmpty()) return emptyList()
        return (first..last).flatMap { d -> times.filter { d > todayEpochDay || it > nowMinute }.map { d to it } }
    }
}
