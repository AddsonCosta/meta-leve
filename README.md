# Meta Leve

App Android para controle de peso e dieta: registre o que come e os exercícios que faz, acompanhe o peso
e veja, a cada dia, quanto ainda pode consumir para chegar à sua meta.

> Nome provisório.

## Funcionalidades (MVP)
- **Cadastro inicial**: dados pessoais, nível de atividade, objetivo (perder, manter ou ganhar), peso desejado e
  ritmo semanal, com previsão de data e alertas de segurança.
- **Hoje**: índice de desempenho do dia, calorias restantes, macronutrientes, peso do dia, atividade do dia,
  anotação, refeições e exercícios. Dá para navegar pelos dias anteriores.
- **Alimentos**: busca sem acentos na tabela TACO (597 alimentos brasileiros), favoritos e alimentos próprios.
- **Exercícios**: 34 atividades com gasto calculado por MET e peso atual, mais exercícios próprios.
- **Histórico**: gráfico de peso com a meta, resumo da dieta e lista de dias.
- **Backup**: exportar e restaurar todos os dados em JSON.

## Cálculos
| Item | Fórmula |
|---|---|
| Taxa de repouso | Mifflin-St Jeor |
| Gasto base do dia | repouso × (1 + fator de atividade) |
| Meta diária | ritmo semanal (kg) × 7700 / 7 |
| Calorias restantes | gasto base + exercício − consumido − meta |
| Índice de desempenho | 100 × (gasto base + exercício − consumido) / meta |
| Exercício | MET × peso (kg) × horas |

O código fica em `core/` (Kotlin puro, com testes).

## Estrutura
- `core/`: modelo e cálculos, sem dependência de Android.
- `app/`: Jetpack Compose + Material 3, Room, Navigation, kotlinx.serialization.

## Build
Requer JDK 17 e Android SDK (API 36).

```bash
./gradlew :core:test            # testes dos cálculos
./gradlew :app:assembleDebug    # APK de debug
./gradlew :app:bundleRelease    # AAB para a Play Store
```

O GitHub Actions compila o APK e o AAB a cada push; os arquivos ficam nos artefatos da execução.
Para assinar o release, defina `METALEVE_KEYSTORE`, `METALEVE_KEYSTORE_PASSWORD`, `METALEVE_KEY_ALIAS` e
`METALEVE_KEY_PASSWORD`.

## Créditos
Ver [NOTICE.md](NOTICE.md). Para publicar, siga [docs/publicacao.md](docs/publicacao.md). O Meta Leve não substitui orientação médica ou de nutricionista.
