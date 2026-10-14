package com.addsoncosta.evolui.health

import android.content.Context
import java.io.File

/** Partes da seção Saúde que têm relatório próprio. */
enum class ReportArea(val label: String) {
    GERAL("Relatório geral de saúde"),
    CONSULTA("Consultas"),
    EXAME("Exames"),
    MEDICACAO("Medicação"),
    ODONTOLOGIA("Odontologia"),
    NUTRICAO("Nutrição"),
    FISIOTERAPIA("Fisioterapia"),
    TERAPIA("Terapia"),
    TRATAMENTO("Tratamentos"),
    BIOIMPEDANCIA("Bioimpedância"),
    ANAMNESE("Anamnese"),
    CALENDARIO("Calendário de saúde"),
    CICLO("Ciclo menstrual"),
}

/** Relatórios da seção Saúde em PDF e texto. (Esboço: substituído pela implementação completa.) */
object HealthReports {
    /** Gera o PDF da área (na pasta de compartilhamento) e devolve o arquivo. */
    suspend fun pdf(context: Context, area: ReportArea): File = File(context.cacheDir, "share/relatorio.pdf")

    /** Resumo em texto simples da área, para WhatsApp ou copiar. */
    suspend fun text(context: Context, area: ReportArea): String = ""
}
