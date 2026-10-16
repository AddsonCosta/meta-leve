package com.addsoncosta.evolui.data

/**
 * Grupo de alimentos procurado por uma palavra geral ("peixe", "fruta", "doce").
 * Entra no grupo o alimento de uma das [categories] (categorias da TACO) ou cujo nome sem acento tem um dos [keywords];
 * fica fora o que tem um dos trechos de [exclude]. Trecho com "^" vale só no começo do nome.
 */
data class FoodGroup(
    val categories: List<String> = emptyList(),
    val keywords: List<String> = emptyList(),
    val exclude: List<String> = emptyList(),
) {
    fun excludes(food: FoodEntity): Boolean = exclude.any { has(food.searchName, it) }

    fun matches(food: FoodEntity): Boolean =
        (food.category in categories || keywords.any { has(food.searchName, it) }) && !excludes(food)

    /** Trechos para a busca no banco (LIKE), sem o "^". */
    val likeTokens: List<String> get() = keywords.map { it.removePrefix("^") }.distinct()

    private fun has(searchName: String, token: String): Boolean =
        if (token.startsWith("^")) searchName.startsWith(token.substring(1)) else searchName.contains(token)
}

/** O que a busca entendeu do texto digitado. */
data class ParsedQuery(
    /** Todas as palavras (sem acento, minúsculas), como antes. */
    val tokens: List<String>,
    /** Grupos reconhecidos (palavras gerais). */
    val groups: List<FoodGroup>,
    /** Palavras que sobram além das gerais (ex.: "assado" em "peixe assado"). */
    val rest: List<String>,
)

/**
 * Sinônimos da busca de alimentos: palavras gerais que trazem os alimentos do grupo mesmo sem estarem no nome
 * (hoje nenhum dos peixes tem "peixe" no nome). As chaves estão sem acento e em minúsculas.
 */
object FoodSynonyms {
    private const val PESCADOS = "Pescados e frutos do mar"
    private const val CARNES = "Carnes e derivados"
    private const val FRUTAS = "Frutas e derivados"
    private const val VERDURAS = "Verduras, hortaliças e derivados"
    private const val CEREAIS = "Cereais e derivados"
    private const val LEGUMINOSAS = "Leguminosas e derivados"
    private const val LEITE = "Leite e derivados"
    private const val BEBIDAS = "Bebidas (alcoólicas e não alcoólicas)"
    private const val ACUCARADOS = "Produtos açucarados"
    private const val GORDURAS = "Gorduras e óleos"
    private const val NOZES = "Nozes e sementes"
    private const val OVOS = "Ovos e derivados"
    private const val PREPARADOS = "Alimentos preparados"

    private val seafood = listOf("camarao", "caranguejo", "^siri", "lula", "polvo", "ostra", "mexilhao", "marisco", "lagosta")
    private val leafy = listOf(
        "alface", "agriao", "acelga", "almeirao", "couve,", "couve ", "espinafre", "rucula", "chicoria", "repolho",
        "catalonha", "mostarda, folha", "taioba", "serralha", "caruru",
    )

    private val table: Map<String, FoodGroup> = buildMap<String, FoodGroup> {
        fun add(keys: List<String>, group: FoodGroup) = keys.forEach { put(it, group) }

        add(listOf("peixe", "peixes", "pescado", "pescados", "peixinho"), FoodGroup(categories = listOf(PESCADOS), exclude = seafood))
        add(
            listOf("frutos do mar", "fruto do mar", "marisco", "mariscos", "crustaceo", "crustaceos"),
            FoodGroup(keywords = seafood),
        )
        add(listOf("carne", "carnes"), FoodGroup(categories = listOf(CARNES), keywords = listOf("carne", "bife")))
        add(
            listOf("carne vermelha", "carnes vermelhas", "boi", "bovina", "bovino", "bife", "bifes", "vaca"),
            FoodGroup(
                keywords = listOf(
                    "bovin", "bife", "picanha", "alcatra", "maminha", "fraldinha", "cupim", "patinho", "acem", "musculo",
                    "lagarto", "contra-file", "file mingnon", "x-burguer", "almondega", "carne",
                ),
                exclude = listOf("porco", "frango", "caldo de"),
            ),
        )
        add(
            listOf("frango", "frangos", "galinha", "galinhas", "ave", "aves", "carne branca"),
            FoodGroup(keywords = listOf("frango", "galinha", "peru", "nuggets")),
        )
        add(
            listOf("porco", "porcos", "suino", "suinos", "suina", "suinas"),
            FoodGroup(
                keywords = listOf(
                    "porco", "lombo", "pernil", "bacon", "toucinho", "bisteca", "presunto", "apresuntado", "salame",
                    "mortadela", "sarapatel", "feijoada", "linguica",
                ),
                exclude = listOf("frango"),
            ),
        )
        add(
            listOf("embutido", "embutidos", "frios"),
            FoodGroup(
                keywords = listOf(
                    "presunto", "apresuntado", "mortadela", "salame", "salsicha", "linguica", "peito de peru", "bacon",
                    "queijo, prato", "queijo, mozarela",
                ),
            ),
        )
        add(listOf("fruta", "frutas", "frutinha", "frutinhas"), FoodGroup(categories = listOf(FRUTAS), keywords = listOf("salada de frutas")))
        add(
            listOf("verdura", "verduras", "folha", "folhas", "folhosa", "folhosas", "folhoso", "folhosos"),
            FoodGroup(keywords = leafy, exclude = listOf("charuto")),
        )
        add(
            listOf("salada", "saladas"),
            FoodGroup(
                keywords = leafy + listOf("salada", "tomate, com semente", "tomate, salada", "pepino", "tabule", "palmito", "rabanete"),
                exclude = listOf("couve, manteiga, refogada", "repolho, roxo, refogado"),
            ),
        )
        add(
            listOf("legume", "legumes", "hortalica", "hortalicas", "vegetal", "vegetais"),
            FoodGroup(
                categories = listOf(VERDURAS),
                keywords = listOf("seleta de legumes", "sopa de legumes", "salada, de legumes"),
                exclude = listOf(
                    "farinha", "fecula", "polvilho", "biscoito", "pao, de queijo", "nhoque", "chips", "farofa",
                    "tomate, extrato", "tomate, molho", "tomate, pure",
                ),
            ),
        )
        add(
            listOf("tuberculo", "tuberculos", "raiz", "raizes"),
            FoodGroup(
                keywords = listOf("batata", "mandioca", "aipim", "inhame", "^cara,", "beterraba", "cenoura", "nabo", "rabanete"),
                exclude = listOf("chips", "farinha", "fecula"),
            ),
        )
        add(listOf("aipim", "macaxeira"), FoodGroup(keywords = listOf("mandioca", "aipim")))
        add(listOf("jerimum"), FoodGroup(keywords = listOf("abobora")))
        add(listOf("bergamota", "mexerica", "tangerina"), FoodGroup(keywords = listOf("mexerica", "tangerina")))
        add(
            listOf("doce", "doces", "docinho", "docinhos", "sobremesa", "sobremesas", "guloseima", "guloseimas"),
            FoodGroup(
                categories = listOf(ACUCARADOS),
                keywords = listOf(
                    "bolo", "pudim", "sorvete", "brigadeiro", "biscoito, doce", "chocolate", "cocada", "quindim", "doce",
                    "em calda", "pacoca", "pe-de-moleque", "picole", "gelatina", "geleia", "creme de avela",
                ),
                exclude = listOf(
                    "batata, doce", "agua doce", "erva-doce", "polvilho", "bolo, mistura", "gelatina, sabores variados, po",
                    "achocolatado", "acucar,", "glicose",
                ),
            ),
        )
        add(
            listOf("bebida", "bebidas", "liquido", "liquidos"),
            FoodGroup(categories = listOf(BEBIDAS), keywords = listOf("suco", "vitamina", "limonada", "bebida")),
        )
        add(
            listOf("alcool", "alcoolica", "alcoolicas", "alcoolico", "alcoolicos", "drink", "drinks", "drinque", "drinques", "birita"),
            FoodGroup(
                keywords = listOf("cerveja", "vinho", "destilado", "aguardente", "vodca", "uisque", "caipirinha"),
                exclude = listOf("sem alcool"),
            ),
        )
        add(
            listOf(
                "laticinio", "laticinios", "lacteo", "lacteos", "lactea", "lacteas",
                "derivado de leite", "derivados de leite", "derivados do leite",
            ),
            FoodGroup(
                categories = listOf(LEITE),
                keywords = listOf("^queijo", "^iogurte", "^requeijao", "^manteiga", "^cream cheese", "^coalhada", "^leite, de vaca"),
                exclude = listOf("soja, queijo"),
            ),
        )
        add(
            listOf("pao", "paes", "paozinho", "paezinhos", "padaria"),
            FoodGroup(
                keywords = listOf("pao", "torrada", "croissant", "wrap", "bisnaga", "broa", "misto quente"),
                exclude = listOf("farinha"),
            ),
        )
        add(
            listOf("massa", "massas", "macarronada", "noodle", "noodles", "espaguete", "talharim", "penne", "miojo"),
            FoodGroup(keywords = listOf("macarrao", "lasanha", "nhoque", "yakisoba", "espaguete", "talharim", "massa")),
        )
        add(
            listOf("grao", "graos"),
            FoodGroup(
                categories = listOf(LEGUMINOSAS),
                keywords = listOf("arroz, ", "quinoa", "aveia", "milho, verde", "milho verde", "canjica", "grao"),
                exclude = listOf("farinha", "pacoca", "pe-de-moleque", "extrato", "pasta de amendoim", "soja, queijo", "curau", "creme de"),
            ),
        )
        add(listOf("leguminosa", "leguminosas"), FoodGroup(categories = listOf(LEGUMINOSAS)))
        add(listOf("cereal", "cereais"), FoodGroup(categories = listOf(CEREAIS), keywords = listOf("granola", "aveia", "cereal")))
        add(
            listOf("castanha", "castanhas", "noz", "nozes", "amendoas", "oleaginosa", "oleaginosas"),
            FoodGroup(
                categories = listOf(NOZES),
                keywords = listOf("castanha", "amendoa", "noz,", "nozes", "amendoim", "macadamia", "pistache", "avela"),
                exclude = listOf(
                    "chocolate", "creme de avela", "leite de amendoas", "pacoca", "pe-de-moleque", "farinha", "coco", "pupunha",
                    "pinhao", "semente",
                ),
            ),
        )
        add(
            listOf("semente", "sementes"),
            FoodGroup(keywords = listOf("semente", "chia", "linhaca", "gergelim", "girassol", "pinhao")),
        )
        add(
            listOf("suco", "sucos", "suquinho", "nectar"),
            FoodGroup(keywords = listOf("suco", "limonada", "nectar", "polpa, congelada", "caldo de cana", "cana, caldo")),
        )
        add(
            listOf("refrigerante", "refrigerantes", "refri", "refris", "soda"),
            FoodGroup(keywords = listOf("refrigerante", "agua tonica")),
        )
        add(listOf("ovo", "ovos"), FoodGroup(categories = listOf(OVOS), keywords = listOf("ovo", "omelete", "crepioca"), exclude = listOf("com ovos")))
        add(
            listOf("gordura", "gorduras", "oleo", "oleos", "azeites"),
            FoodGroup(
                categories = listOf(GORDURAS),
                // "sem gordura"/"com gordura" nas carnes ficam no fim da lista.
                exclude = listOf("sem gordura", "com gordura", "capa de gordura", "vegetal", "pipoca"),
            ),
        )
        add(
            listOf("lanche", "lanches", "fast food", "fastfood", "sanduiche", "sanduiches", "sanduba", "hamburguer", "hamburgueres"),
            FoodGroup(
                keywords = listOf(
                    "sanduiche", "x-burguer", "misto quente", "cachorro-quente", "hamburguer", "pizza", "esfiha", "coxinha",
                    "pastel", "wrap", "pao de hamburguer",
                ),
                exclude = listOf("hamburguer, bovino, cru", "pastel, massa", "pastel, de carne, cru", "pastel, de queijo, cru"),
            ),
        )
        add(
            listOf("salgado", "salgados", "salgadinho", "salgadinhos"),
            FoodGroup(
                keywords = listOf(
                    "coxinha", "pastel", "empada", "esfiha", "quibe", "croquete", "pao, de queijo", "acaraje", "bolinho", "chips",
                ),
                exclude = listOf("bacalhau", "porco", ", cru", "massa"),
            ),
        )
        add(
            listOf(
                "prato pronto", "pratos prontos", "comida pronta", "comida caseira", "comida tipica", "comidas tipicas",
                "preparado", "preparados", "tipico", "tipicos", "tipica", "tipicas", "regional",
            ),
            FoodGroup(categories = listOf(PREPARADOS)),
        )
        add(
            listOf("tempero", "temperos", "condimento", "condimentos", "molho", "molhos"),
            FoodGroup(
                keywords = listOf(
                    "molho", "ketchup", "mostarda", "maionese", "shoyu", "tempero", "^sal,", "^alho,", "^cebola,", "^salsa,",
                    "cebolinha", "coentro", "manjericao", "alfavaca", "caldo de carne", "caldo de galinha",
                ),
                exclude = listOf("mostarda, folha", "alho-poro"),
            ),
        )
        add(listOf("sopa", "sopas", "caldo", "caldos"), FoodGroup(keywords = listOf("sopa", "caldo verde", "canja", "caldo de")))
        add(
            listOf("cha", "chas", "infusao"),
            FoodGroup(keywords = listOf("^cha,", "^cha ", "mate, infusao"), exclude = listOf("charque", "charuto", "chantilly", "bolacha", "salsicha")),
        )
        add(listOf("bolacha", "bolachas", "biscoitos"), FoodGroup(keywords = listOf("biscoito", "bolacha")))
        add(
            listOf("proteina", "proteinas"),
            FoodGroup(
                keywords = listOf(
                    "whey", "frango, peito", "ovo, de galinha", "atum", "tilapia", "patinho", "iogurte grego", "queijo, cottage",
                    "tofu", "file mingnon", "lombo",
                ),
                exclude = listOf("gema", ", cru"),
            ),
        )
        add(
            listOf("carboidrato", "carboidratos", "carbo", "carbos"),
            FoodGroup(
                keywords = listOf(
                    "arroz, ", "pao", "macarrao", "batata", "mandioca", "tapioca", "aveia", "cuscuz", "quinoa", "milho verde",
                ),
                exclude = listOf("farinha", "chips", ", cru", ", crua", "creme de"),
            ),
        )
    }

    /** Palavras ligadas que não ajudam a filtrar ("carne de porco"). */
    private val stopWords = setOf("de", "da", "do", "das", "dos", "com", "e", "a", "o", "em", "na", "no", "ao", "sem")

    private val phrases: List<String> = table.keys.filter { ' ' in it }.sortedByDescending { it.length }

    /** Entende o texto já normalizado: palavras, grupos (inclusive expressões como "frutos do mar") e o que sobra. */
    fun parse(normalized: String): ParsedQuery {
        val tokens = split(normalized)
        val groups = mutableListOf<FoodGroup>()
        var text = " " + tokens.joinToString(" ") + " "
        for (p in phrases) {
            if (text.contains(" $p ")) {
                table[p]?.let { if (it !in groups) groups += it }
                text = text.replace(" $p ", " ")
            }
        }
        val rest = mutableListOf<String>()
        split(text).forEach { t ->
            val g = table[t] ?: table[singular(t)]
            if (g != null) {
                if (g !in groups) groups += g
            } else if (t !in stopWords) {
                rest += t
            }
        }
        return ParsedQuery(tokens, groups, rest)
    }

    private fun split(text: String) = text.split(Regex("[^a-z0-9]+")).filter { it.isNotEmpty() }

    /** Plural comum do português para o singular ("feijoes" → "feijao", "paes" → "pao", "pasteis" → "pastel"). */
    fun singular(t: String): String = when {
        t.length > 4 && t.endsWith("oes") -> t.dropLast(3) + "ao"
        t.length > 3 && t.endsWith("aes") -> t.dropLast(3) + "ao"
        t.length > 4 && t.endsWith("eis") -> t.dropLast(3) + "el"
        t.length > 4 && t.endsWith("ais") -> t.dropLast(3) + "al"
        t.length > 3 && t.endsWith("ns") -> t.dropLast(2) + "m"
        t.length > 3 && t.endsWith("s") -> t.dropLast(1)
        else -> t
    }

    /**
     * Formas da palavra para achar plural e feminino/masculino: ela mesma e a raiz do singular
     * ("assadas" → "assad", que acha "assado"; "feijoes" → "feija", que acha "feijao"; "moida" → "moid").
     */
    fun variants(t: String): List<String> {
        val s = singular(t)
        val root = if (s.length >= 5 && s.last() in "aoe") s.dropLast(1) else s
        return if (root == t || t.contains(root)) listOf(root) else listOf(t, root)
    }
}
