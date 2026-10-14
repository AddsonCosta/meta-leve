package com.addsoncosta.evolui.data

/** Referência comum de água por dia: 35 ml por kg de peso. */
const val WATER_ML_PER_KG = 35.0

/** Meta de água do dia em ml: a escolhida pelo usuário ou 35 ml por kg. */
fun waterGoalMl(weightKg: Double, override: Double?): Double = override ?: (weightKg * WATER_ML_PER_KG)
