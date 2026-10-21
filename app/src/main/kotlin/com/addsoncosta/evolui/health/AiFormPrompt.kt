package com.addsoncosta.evolui.health

import com.addsoncosta.evolui.data.HealthType
import java.time.LocalDate

/**
 * Comando fixo para a IA do usuário ("Ler com minha IA") ler o documento anexado e devolver
 * os campos do formulário em JSON, e a conversão da resposta. Puro, testado na JVM.
 */
object AiFormPrompt {
    const val KEY_DATE = "data"
    const val KEY_TITLE = "titulo"
    const val KEY_PROFESSIONAL = "profissional"
    const val KEY_PLACE = "local"
    const val KEY_MEDICINES = "medicamentos"

    /** Áreas em que o documento pode ser uma receita com vários remédios. */
    fun wantsMedicines(type: HealthType): Boolean =
        type == HealthType.MEDICACAO || type == HealthType.CONSULTA || type == HealthType.SUPLEMENTACAO

    /** Campos extras pedidos à IA (sem os internos, como o tipo de exame). */
    fun extraFields(type: HealthType, reportExam: Boolean): List<ExtraField> {
        val spec = HealthFields.spec(type)
        val base = spec.extras.filter { it.key != HealthFields.EYE_DIAGNOSES && it.key != HealthFields.EXAM_KIND }
        return if (type == HealthType.EXAME && reportExam) base + HealthFields.reportFields else base
    }

    fun instructions(type: HealthType, reportExam: Boolean = false): String {
        val spec = HealthFields.spec(type)
        val lines = mutableListOf<String>()
        lines += "\"$KEY_DATE\": data do documento (atendimento, coleta ou emissão), no formato AAAA-MM-DD"
        lines += "\"$KEY_TITLE\": ${spec.titleLabel.removeSuffix(" *")} (${spec.titleHelp})"
        lines += "\"$KEY_PROFESSIONAL\": ${spec.professionalLabel} (nome; inclua o registro, como CRM, se aparecer)"
        lines += "\"$KEY_PLACE\": ${spec.placeLabel.removeSuffix(" *")} (${spec.placeHelp})"
        extraFields(type, reportExam).forEach { f ->
            val format = when (f.kind) {
                FieldKind.DATE -> " — formato AAAA-MM-DD"
                FieldKind.NUMBER -> " — só o número inteiro"
                else -> ""
            }
            lines += "\"${f.key}\": ${f.label} (${f.help})$format"
        }
        if (wantsMedicines(type)) {
            lines += "\"$KEY_MEDICINES\": lista de remédios ou suplementos receitados, cada um como " +
                "{\"nome\": \"...\", \"dose\": \"concentração e quantidade, ex.: 500 mg, 1 comprimido\", " +
                "\"posologia\": \"como tomar, ex.: de 8 em 8 horas\", \"duracao\": \"ex.: por 7 dias ou uso contínuo\"}"
        }
        return buildString {
            appendLine("Você vai ler um documento de saúde em português do Brasil (área: ${type.label}).")
            appendLine("As imagens são as páginas do documento; o texto lido no celular (OCR) pode vir junto e pode ter erros.")
            appendLine("Responda SOMENTE com um objeto JSON, sem comentários, com estas chaves:")
            lines.forEach { appendLine("- $it") }
            appendLine("Use texto simples. Deixe de fora (ou use null) a chave que o documento não mostra. Não invente dados.")
        }.trim()
    }

    /**
     * Converte a resposta (já como mapa de texto) num [AutoFill].
     * [medicines] são os itens de "medicamentos" (nome, dose, posologia, duracao).
     */
    fun toAutoFill(
        type: HealthType,
        fields: Map<String, String>,
        medicines: List<Map<String, String>> = emptyList(),
        reportExam: Boolean = false,
    ): AutoFill {
        fun v(key: String): String? = fields[key]?.trim()?.takeIf { it.isNotEmpty() && it.lowercase() != "null" }
        val date = v(KEY_DATE)?.let { runCatching { LocalDate.parse(it.take(10)) }.getOrNull() }
        val extras = buildMap {
            extraFields(type, reportExam).forEach { f ->
                val value = v(f.key) ?: return@forEach
                when (f.kind) {
                    FieldKind.DATE -> runCatching { LocalDate.parse(value.take(10)) }.getOrNull()?.let { put(f.key, it.toString()) }
                    FieldKind.NUMBER -> value.filter { it.isDigit() }.takeIf { it.isNotEmpty() }?.let { put(f.key, it) }
                    else -> put(f.key, value)
                }
            }
        }
        val meds = medicines.mapNotNull { m ->
            val name = m["nome"]?.trim()?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            val duration = m["duracao"]?.trim()?.takeIf { it.isNotEmpty() }
            val folded = duration?.let { TextScan.fold(it) }.orEmpty()
            val continuous = folded.contains("continu")
            val days = Regex("(\\d{1,3})\\s*(dias?|semanas?|meses|mes)").find(folded)?.let { d ->
                val n = d.groupValues[1].toInt()
                when {
                    d.groupValues[2].startsWith("dia") -> n
                    d.groupValues[2].startsWith("semana") -> n * 7
                    else -> n * 30
                }
            }
            val posology = m["posologia"]?.trim()?.takeIf { it.isNotEmpty() }
            PrescribedMedicine(
                name = name,
                dose = m["dose"]?.trim()?.takeIf { it.isNotEmpty() },
                posology = posology,
                duration = if (continuous) "Uso contínuo" else duration,
                durationDays = if (continuous) null else days,
                continuous = continuous,
                asNeeded = posology?.let { Regex("(?<![a-z])(se dor|se febre|se necessario|quando necessario|s/n)").containsMatchIn(TextScan.fold(it)) } ?: false,
                line = listOfNotNull(name, m["dose"], posology, duration).joinToString(" · "),
            )
        }
        val prescription = if (meds.isEmpty()) null else Prescription(date = date, doctor = v(KEY_PROFESSIONAL), medicines = meds, looksLikePrescription = true)
        // Medicação: o primeiro remédio da lista completa o formulário.
        val first = meds.firstOrNull()?.takeIf { type == HealthType.MEDICACAO || type == HealthType.SUPLEMENTACAO }
        val fromFirst = when {
            first == null -> emptyMap()
            type == HealthType.MEDICACAO -> DocumentAutoFill.medicineExtras(first, date)
            else -> listOfNotNull(first.dose?.let { "dose" to it }, first.posology?.let { "horario" to it }).toMap()
        }
        return AutoFill(
            date = date,
            title = v(KEY_TITLE) ?: first?.name,
            professional = v(KEY_PROFESSIONAL),
            place = v(KEY_PLACE),
            extras = fromFirst + extras,
            prescription = prescription,
        )
    }
}
