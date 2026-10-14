package com.addsoncosta.evolui.health

import com.addsoncosta.evolui.data.HealthType

enum class FieldKind { TEXT, LONG_TEXT, DATE, NUMBER }

/** Campo específico de um tipo de registro, guardado no JSON `extra`. */
data class ExtraField(val key: String, val label: String, val help: String, val kind: FieldKind = FieldKind.TEXT)

/** Rótulos, ajudas e campos do formulário de cada área da Saúde. */
data class TypeSpec(
    val titleLabel: String,
    val titleHelp: String,
    val professionalLabel: String,
    val professionalHelp: String,
    val placeLabel: String,
    val placeHelp: String,
    val placeRequired: Boolean = false,
    val descriptionLabel: String = "Descrição e observações",
    val descriptionHelp: String = "Tudo o que quiser lembrar depois. Ex.: o que foi conversado, como se sentiu.",
    val statuses: List<String> = emptyList(),
    val extras: List<ExtraField> = emptyList(),
    /** Este tipo tem tabela de valores (exames e bioimpedância). */
    val hasValues: Boolean = false,
)

object HealthFields {
    private val sessionStatuses = listOf("Em andamento", "Concluído", "Pausado")

    private fun sessionExtras(goalHelp: String) = listOf(
        ExtraField("objetivo", "Objetivo", goalHelp),
        ExtraField("frequencia", "Frequência", "Ex.: 2 vezes por semana"),
        ExtraField("sessoes", "Número de sessões", "Quantas sessões foram feitas ou indicadas. Ex.: 10", FieldKind.NUMBER),
    )

    fun spec(type: HealthType): TypeSpec = when (type) {
        HealthType.CONSULTA -> TypeSpec(
            titleLabel = "Título", titleHelp = "Ex.: Consulta com cardiologista",
            professionalLabel = "Médico(a)", professionalHelp = "Ex.: Dra. Ana Souza",
            placeLabel = "Clínica ou hospital", placeHelp = "Ex.: Clínica Bem Estar",
            descriptionLabel = "Anotações da consulta",
            extras = listOf(
                ExtraField("especialidade", "Especialidade", "Ex.: Cardiologia, clínico geral"),
                ExtraField("motivo", "Motivo da consulta", "Ex.: dor no peito, check-up anual", FieldKind.LONG_TEXT),
                ExtraField("diagnostico", "Diagnóstico e orientações", "O que o médico disse e recomendou", FieldKind.LONG_TEXT),
                ExtraField("retorno", "Retorno", "Data do retorno, se houver", FieldKind.DATE),
            ),
        )
        HealthType.EXAME -> TypeSpec(
            titleLabel = "Nome do exame", titleHelp = "Ex.: Hemograma completo e bioquímica",
            professionalLabel = "Médico que pediu", professionalHelp = "Ex.: Dr. Carlos Lima (opcional)",
            placeLabel = "Laboratório *", placeHelp = "Onde o exame foi feito. Ex.: Laboratório Sabin",
            placeRequired = true,
            descriptionLabel = "Resultado e observações",
            descriptionHelp = "Conclusão do laudo ou o que o médico falou do resultado.",
            statuses = listOf("Aguardando resultado", "Resultado pronto"),
            extras = listOf(ExtraField("pedido", "Motivo do pedido", "Ex.: rotina, acompanhamento da tireoide")),
            hasValues = true,
        )
        HealthType.MEDICACAO -> TypeSpec(
            titleLabel = "Nome do remédio", titleHelp = "Ex.: Losartana",
            professionalLabel = "Quem receitou", professionalHelp = "Ex.: Dra. Ana Souza",
            placeLabel = "Farmácia ou local", placeHelp = "Opcional. Ex.: Farmácia Popular",
            descriptionLabel = "Observações",
            descriptionHelp = "Ex.: tomar em jejum, efeitos que sentiu.",
            statuses = listOf("Em uso", "Suspenso", "Concluído"),
            extras = listOf(
                ExtraField("dose", "Dose", "Ex.: 50 mg, 1 comprimido"),
                ExtraField("frequencia", "Frequência", "Ex.: 1 vez ao dia, de 8 em 8 horas"),
                ExtraField("inicio", "Início", "Quando começou a tomar", FieldKind.DATE),
                ExtraField("fim", "Fim", "Quando parou ou deve parar (deixe vazio se for contínuo)", FieldKind.DATE),
                ExtraField("motivo", "Para que serve", "Ex.: pressão alta"),
            ),
        )
        HealthType.ODONTOLOGIA -> TypeSpec(
            titleLabel = "Título", titleHelp = "Ex.: Limpeza e restauração",
            professionalLabel = "Dentista", professionalHelp = "Ex.: Dr. Paulo Mendes",
            placeLabel = "Clínica", placeHelp = "Ex.: Odonto Sorriso",
            descriptionLabel = "Descrição do atendimento",
            statuses = listOf("Planejado", "Em andamento", "Concluído"),
            extras = listOf(
                ExtraField("procedimento", "Procedimento", "Ex.: restauração, canal, extração, limpeza"),
                ExtraField("dentes", "Dentes", "Números dos dentes separados por vírgula. Ex.: 16, 26 (veja a arcada dentária)"),
                ExtraField("tratamento", "Tratamento indicado", "Ex.: aparelho, implante, clareamento", FieldKind.LONG_TEXT),
            ),
        )
        HealthType.NUTRICAO -> TypeSpec(
            titleLabel = "Título", titleHelp = "Ex.: Consulta com nutricionista",
            professionalLabel = "Nutricionista", professionalHelp = "Ex.: Juliana Reis",
            placeLabel = "Local", placeHelp = "Ex.: Clínica Nutrir",
            descriptionLabel = "Plano e orientações",
            statuses = sessionStatuses,
            extras = sessionExtras("Ex.: perder 5 kg, melhorar o colesterol"),
        )
        HealthType.FISIOTERAPIA -> TypeSpec(
            titleLabel = "Título", titleHelp = "Ex.: Fisioterapia para o joelho",
            professionalLabel = "Fisioterapeuta", professionalHelp = "Ex.: Marcos Alves",
            placeLabel = "Local", placeHelp = "Ex.: Clínica Movimento",
            descriptionLabel = "Exercícios e evolução",
            statuses = sessionStatuses,
            extras = sessionExtras("Ex.: recuperar o movimento do joelho"),
        )
        HealthType.TERAPIA -> TypeSpec(
            titleLabel = "Título", titleHelp = "Ex.: Terapia semanal",
            professionalLabel = "Terapeuta / psicólogo(a)", professionalHelp = "Ex.: Dra. Beatriz Lopes",
            placeLabel = "Local", placeHelp = "Ex.: Consultório ou on-line",
            descriptionLabel = "Anotações",
            statuses = sessionStatuses,
            extras = sessionExtras("Ex.: lidar melhor com a ansiedade"),
        )
        HealthType.TRATAMENTO -> TypeSpec(
            titleLabel = "Nome do tratamento", titleHelp = "Ex.: Tratamento para hipertensão",
            professionalLabel = "Profissional responsável", professionalHelp = "Ex.: Dr. Carlos Lima",
            placeLabel = "Local", placeHelp = "Ex.: Hospital São Lucas",
            descriptionLabel = "Como está o tratamento",
            statuses = sessionStatuses,
            extras = sessionExtras("Ex.: controlar a pressão"),
        )
        HealthType.BIOIMPEDANCIA -> TypeSpec(
            titleLabel = "Título", titleHelp = "Ex.: Bioimpedância de rotina",
            professionalLabel = "Profissional", professionalHelp = "Ex.: Juliana Reis (opcional)",
            placeLabel = "Local ou aparelho", placeHelp = "Ex.: Academia Força, balança InBody",
            descriptionLabel = "Observações",
            hasValues = true,
        )
        HealthType.ANAMNESE -> TypeSpec(
            titleLabel = "Título", titleHelp = "Ex.: Anamnese",
            professionalLabel = "Profissional", professionalHelp = "Opcional",
            placeLabel = "Local", placeHelp = "Opcional",
        )
    }

    /** Rótulo legível de uma chave do JSON `extra` (também para chaves de outras telas). */
    fun label(type: HealthType?, key: String): String =
        type?.let { t -> spec(t).extras.firstOrNull { it.key == key }?.label }
            ?: key.replace('_', ' ').replaceFirstChar { it.uppercase() }

    fun kind(type: HealthType?, key: String): FieldKind =
        type?.let { t -> spec(t).extras.firstOrNull { it.key == key }?.kind } ?: FieldKind.TEXT

    fun typeOf(name: String): HealthType? = HealthType.entries.firstOrNull { it.name == name }
}
