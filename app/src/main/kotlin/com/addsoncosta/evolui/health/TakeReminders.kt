package com.addsoncosta.evolui.health

import android.content.Context
import com.addsoncosta.evolui.data.HealthEventDao
import com.addsoncosta.evolui.data.HealthEventEntity
import com.addsoncosta.evolui.data.HealthRecordEntity
import com.addsoncosta.evolui.data.HealthRepository
import com.addsoncosta.evolui.data.HealthType
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * Lembretes de tomar (remédios e suplementos) e registros de Medicação criados a partir da receita.
 * Os lembretes são eventos do calendário de saúde, um por horário, avisando na hora (como a opção
 * "Repetir todo dia" do calendário).
 */
object TakeReminders {
    /** Limite de avisos criados de uma vez. */
    private const val MAX_EVENTS = 400
    private val br = DateTimeFormatter.ofPattern("dd/MM/yyyy")

    /** Plano de lembretes de um registro: horários e por quantos dias, a partir de quando. */
    data class Plan(val times: List<Int>, val startDay: Long, val days: Int) {
        val isEmpty get() = times.isEmpty() || days <= 0
        /** "às 08:00 e 20:00, por 7 dias". */
        fun describe(): String {
            val t = times.map { "%02d:%02d".format(it / 60, it % 60) }
            val list = if (t.size <= 1) t.joinToString() else t.dropLast(1).joinToString(", ") + " e " + t.last()
            return "às $list, por $days ${if (days == 1) "dia" else "dias"}"
        }
    }

    /** Plano a partir dos campos do registro (frequência/horário, início e fim). */
    fun planFor(type: HealthType, epochDay: Long, extra: Map<String, String>): Plan {
        val freq = if (type == HealthType.SUPLEMENTACAO) extra["horario"] else extra["frequencia"]
        val times = MedicationSchedule.times(freq).ifEmpty { MedicationSchedule.perDayTimes(1) }
        val today = LocalDate.now().toEpochDay()
        val start = HealthRepository.parseDate(extra["inicio"])?.toEpochDay() ?: maxOf(epochDay, today)
        val end = HealthRepository.parseDate(extra["fim"])?.toEpochDay()
        // Com fim marcado, vale o período da receita; sem fim (uso contínuo), os próximos 30 dias.
        if (end != null && end >= start) return Plan(times, start, (end - start + 1).toInt().coerceAtMost(MedicationSchedule.MAX_DAYS))
        return Plan(times, maxOf(start, today), MedicationSchedule.CONTINUOUS_DAYS)
    }

    /** Cria os avisos e devolve quantos foram criados. Chamar fora da thread principal. */
    suspend fun create(
        context: Context,
        dao: HealthEventDao,
        type: HealthType,
        recordId: Long?,
        name: String,
        dose: String?,
        plan: Plan,
    ): Int {
        val now = LocalTime.now()
        val slots = MedicationSchedule.slots(
            plan.startDay, plan.days, plan.times,
            LocalDate.now().toEpochDay(), now.hour * 60 + now.minute,
        ).take(MAX_EVENTS)
        if (slots.isEmpty()) return 0
        val verb = if (type == HealthType.SUPLEMENTACAO) "Tomar suplemento" else "Tomar"
        val title = "$verb $name".trim()
        val events = slots.map { (day, minute) ->
            HealthEventEntity(
                type = type.name,
                epochDay = day,
                minuteOfDay = minute,
                title = title,
                notes = dose?.takeIf { it.isNotBlank() },
                remindMinutesBefore = 0,
                recordId = recordId,
            )
        }
        val ids = dao.insertMany(events)
        events.zip(ids).forEach { (e, id) -> Reminders.schedule(context, e.copy(id = id)) }
        return events.size
    }

    /** Lembretes de um registro já salvo de Medicação ou Suplementação. */
    suspend fun createFor(context: Context, dao: HealthEventDao, record: HealthRecordEntity, plan: Plan? = null): Int {
        val type = HealthFields.typeOf(record.type) ?: HealthType.MEDICACAO
        val extra = HealthRepository.decodeExtra(record.extra)
        val p = plan ?: planFor(type, record.epochDay, extra)
        return create(context, dao, type, record.id, record.title, extra["dose"], p)
    }

    /**
     * Salva cada remédio escolhido da receita como um registro de Medicação ("Em uso").
     * Com [reminders], cria também os avisos (menos dos remédios "se precisar").
     * Devolve (registros criados, avisos criados).
     */
    suspend fun saveMedicines(
        context: Context,
        repo: HealthRepository,
        dao: HealthEventDao,
        prescription: Prescription,
        medicines: List<PrescribedMedicine>,
        reminders: Boolean,
    ): Pair<Int, Int> {
        val day = prescription.date ?: LocalDate.now()
        var alarms = 0
        medicines.forEach { m ->
            val extra = DocumentAutoFill.medicineExtras(m, day)
            val note = listOfNotNull(
                "Da receita de ${day.format(br)}" + (prescription.doctor?.let { " ($it)" } ?: "") + ".",
                m.duration?.let { "Duração: $it." },
                m.line.takeIf { it.isNotBlank() }?.let { "Linha lida: $it" },
            ).joinToString("\n")
            val record = HealthRecordEntity(
                type = HealthType.MEDICACAO.name,
                epochDay = day.toEpochDay(),
                title = m.name,
                professional = prescription.doctorWithCrm,
                description = note,
                status = "Em uso",
                extra = HealthRepository.encodeExtra(extra),
                createdMillis = 0L,
                updatedMillis = 0L,
            )
            val id = repo.save(record)
            if (reminders && !m.asNeeded) {
                alarms += createFor(context, dao, record.copy(id = id))
            }
        }
        return medicines.size to alarms
    }
}
