package com.addsoncosta.evolui.ui.food

import android.graphics.Bitmap
import com.addsoncosta.evolui.data.FoodEntity
import com.addsoncosta.evolui.data.FoodRepository
import com.addsoncosta.evolui.data.Repository
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabel
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

/**
 * Algo que o estimador viu no prato.
 * [foods] são nomes de alimentos do app (como em `searchName`, sem acento) ou termos de busca em português;
 * os [checked] primeiros já vêm marcados. [grams] fica nulo quando o estimador não sabe a quantidade
 * (o do aparelho não sabe; um estimador na nuvem poderia saber).
 */
data class PlateGuess(
    val label: String,
    val confidence: Float,
    val foods: List<String>,
    val checked: Int = 1,
    val grams: Double? = null,
) {
    /** Rótulo geral ("comida", "prato"), que não aponta um alimento. */
    val generic: Boolean get() = foods.isEmpty()
}

/** Estima o que há no prato a partir da foto. Hoje só no aparelho; um serviço na nuvem pode implementar o mesmo. */
interface PlateEstimator {
    suspend fun estimate(bitmap: Bitmap): List<PlateGuess>
}

/** Reconhecimento no próprio celular (ML Kit, modelo básico de rótulos). A foto não sai do aparelho. */
class OnDevicePlateEstimator : PlateEstimator {
    override suspend fun estimate(bitmap: Bitmap): List<PlateGuess> = withContext(Dispatchers.Default) {
        val labeler = ImageLabeling.getClient(ImageLabelerOptions.Builder().setConfidenceThreshold(0.4f).build())
        try {
            val image = InputImage.fromBitmap(bitmap, 0)
            val labels: List<ImageLabel> = suspendCancellableCoroutine { cont ->
                labeler.process(image)
                    .addOnSuccessListener { cont.resume(it) }
                    .addOnFailureListener { cont.resumeWithException(it) }
            }
            PlateLabels.guesses(labels.map { it.text to it.confidence })
        } finally {
            runCatching { labeler.close() }
        }
    }
}

/** Sugestão pronta para a tela: o alimento do banco, se já vem marcado e a quantidade estimada (se houver). */
data class PlateSuggestion(val food: FoodEntity, val checked: Boolean, val grams: Double?)

/** Troca os nomes dos palpites pelos alimentos do banco (nome exato primeiro; senão, o primeiro da busca). */
suspend fun resolveGuesses(foods: FoodRepository, guesses: List<PlateGuess>, max: Int = 12): List<PlateSuggestion> {
    val exact = foods.foodsByNames(guesses.flatMap { g -> g.foods.map { Repository.normalize(it) } }).associateBy { it.searchName }
    val out = LinkedHashMap<Long, PlateSuggestion>()
    for (g in guesses.sortedByDescending { it.confidence }) {
        g.foods.forEachIndexed { i, name ->
            val food = exact[Repository.normalize(name)] ?: foods.bestMatch(name) ?: return@forEachIndexed
            val old = out[food.id]
            if (old == null) {
                out[food.id] = PlateSuggestion(food, i < g.checked, g.grams)
            } else if (!old.checked && i < g.checked) {
                out[food.id] = old.copy(checked = true)
            }
        }
    }
    return out.values.take(max)
}

/** Rótulos em inglês do modelo básico do ML Kit → alimentos do app. Rótulos que não estão aqui são ignorados. */
object PlateLabels {
    private class Entry(val pt: String, val foods: List<String>, val checked: Int = 1)

    /** Rótulos gerais: mostram que é comida, mas não qual. */
    private val generic = mapOf(
        "food" to "comida", "dish" to "prato", "cuisine" to "culinária", "meal" to "refeição", "lunch" to "almoço",
        "dinner" to "jantar", "supper" to "jantar", "breakfast" to "café da manhã", "brunch" to "refeição",
        "recipe" to "receita", "ingredient" to "ingredientes", "plate" to "prato", "tableware" to "prato",
        "dishware" to "prato", "bowl" to "tigela", "bento" to "marmita", "lunchbox" to "marmita", "fast food" to "lanche",
        "junk food" to "lanche", "comfort food" to "comida caseira", "staple food" to "comida", "finger food" to "petisco",
        "produce" to "alimentos frescos", "snack" to "lanche", "garnish" to "acompanhamento", "side dish" to "acompanhamento",
    )

    private val salad = listOf("alface, crespa, crua", "tomate, salada")
    private val fruits = listOf("banana, prata, crua", "maca, fuji, com casca, crua", "mamao, papaia, cru", "laranja, pera, crua")
    private val vegetables = listOf("brocolis, cozido", "cenoura, cozida", "abobrinha, italiana, cozida", "chuchu, cozido")
    private val meats = listOf(
        "carne, bovina, patinho, sem gordura, grelhado", "frango, peito, sem pele, grelhado", "porco, lombo, assado",
    )
    private val fish = listOf("tilapia, file, grelhada", "salmao, sem pele, fresco, grelhado", "sardinha, assada")
    private val sweets = listOf("pudim de leite condensado", "sorvete, de creme", "brigadeiro", "bolo, pronto, chocolate")

    private val table: Map<String, Entry> = buildMap<String, Entry> {
        fun add(keys: List<String>, e: Entry) = keys.forEach { put(it, e) }

        add(listOf("rice", "fried rice", "steamed rice", "white rice"), Entry("arroz", listOf("arroz, tipo 1, cozido")))
        add(listOf("bean", "beans", "legume", "black beans"), Entry("feijão", listOf("feijao, carioca, cozido", "feijao, preto, cozido")))
        add(listOf("bread", "baked goods", "bun", "roll", "baguette"), Entry("pão", listOf("pao, trigo, frances", "pao de forma, tradicional (branco)")))
        add(listOf("toast"), Entry("torrada", listOf("torrada, pao frances")))
        add(listOf("sandwich", "sub sandwich"), Entry("sanduíche", listOf("sanduiche natural de frango", "misto quente")))
        add(listOf("hamburger", "cheeseburger", "burger", "veggie burger"), Entry("hambúrguer", listOf("x-burguer (com queijo)", "hamburguer (sanduiche simples)")))
        add(listOf("hot dog"), Entry("cachorro-quente", listOf("cachorro-quente")))
        add(listOf("pizza"), Entry("pizza", listOf("pizza, mucarela", "pizza, calabresa")))
        add(listOf("pasta", "spaghetti", "noodle", "noodles", "macaroni", "linguine", "penne"), Entry("macarrão", listOf("macarrao, trigo, cozido", "macarrao, molho bolognesa")))
        add(listOf("lasagna", "lasagne"), Entry("lasanha", listOf("lasanha a bolonhesa")))
        add(listOf("salad", "leaf vegetable", "lettuce", "greens"), Entry("salada", salad, checked = 2))
        add(listOf("vegetable", "vegetables", "root vegetable"), Entry("legumes", vegetables, checked = 0))
        add(listOf("fruit", "fruits", "natural foods", "superfood"), Entry("fruta", fruits, checked = 0))
        add(listOf("meat", "red meat", "roasting", "barbecue", "grilling"), Entry("carne", meats))
        add(listOf("steak", "beef", "sirloin steak", "rib eye steak", "roast beef"), Entry("bife", listOf("carne, bovina, contra-file, sem gordura, grelhado", "carne, bovina, picanha, sem gordura, grelhada")))
        add(listOf("chicken", "poultry", "fried chicken", "chicken meat"), Entry("frango", listOf("frango, peito, sem pele, grelhado", "frango, coxa, com pele, assada")))
        add(listOf("pork"), Entry("carne de porco", listOf("porco, lombo, assado", "porco, bisteca, grelhada")))
        add(listOf("sausage", "bratwurst"), Entry("linguiça", listOf("linguica, porco, grelhada", "salsicha (tipo hot dog)")))
        add(listOf("bacon"), Entry("bacon", listOf("bacon, frito")))
        add(listOf("fish", "seafood"), Entry("peixe", fish))
        add(listOf("shrimp", "prawn", "crustacean"), Entry("camarão", listOf("camarao, rio grande, grande, cozido")))
        add(listOf("sushi", "sashimi"), Entry("sushi", listOf("sushi (uramaki / hossomaki)")))
        add(listOf("egg", "eggs", "fried egg", "omelette", "scrambled eggs", "egg yolk", "boiled egg"), Entry("ovo", listOf("ovo, de galinha, inteiro, frito", "ovo, de galinha, inteiro, cozido/10minutos", "ovo, de galinha, mexido")))
        add(listOf("cheese", "dairy"), Entry("queijo", listOf("queijo, minas, frescal", "queijo, mozarela")))
        add(listOf("milk"), Entry("leite", listOf("leite, de vaca, integral")))
        add(listOf("yogurt", "yoghurt"), Entry("iogurte", listOf("iogurte, natural")))
        add(listOf("cake", "cupcake", "baking", "chocolate cake"), Entry("bolo", listOf("bolo, pronto, chocolate")))
        add(listOf("cookie", "cookies and crackers", "biscuit", "cracker"), Entry("biscoito", listOf("biscoito, doce, maisena", "biscoito, salgado, cream cracker")))
        add(listOf("chocolate"), Entry("chocolate", listOf("chocolate, ao leite")))
        add(listOf("dessert", "sweetness", "confectionery", "candy", "frozen dessert"), Entry("sobremesa", sweets, checked = 0))
        add(listOf("ice cream", "gelato"), Entry("sorvete", listOf("sorvete, de creme")))
        add(listOf("pudding", "flan", "custard"), Entry("pudim", listOf("pudim de leite condensado")))
        add(listOf("coffee", "espresso", "coffee cup"), Entry("café", listOf("cafe, infusao 10%")))
        add(listOf("cappuccino", "latte", "caffe latte"), Entry("café com leite", listOf("cafe com leite (sem acucar)")))
        add(listOf("tea"), Entry("chá", listOf("cha, preto, infusao 5%")))
        add(listOf("juice", "orange juice"), Entry("suco", listOf("laranja, pera, suco", "suco de fruta, nectar (caixinha)")))
        add(listOf("soft drink", "cola", "soda", "carbonated soft drinks"), Entry("refrigerante", listOf("refrigerante, tipo cola", "refrigerante, tipo guarana")))
        add(listOf("drink", "beverage"), Entry("bebida", listOf("suco de fruta, nectar (caixinha)", "refrigerante, tipo cola"), checked = 0))
        add(listOf("beer"), Entry("cerveja", listOf("cerveja, pilsen 2")))
        add(listOf("wine", "red wine"), Entry("vinho", listOf("vinho, tinto")))
        add(listOf("smoothie", "milkshake"), Entry("vitamina", listOf("vitamina de banana com leite")))
        add(listOf("soup", "broth"), Entry("sopa", listOf("sopa de legumes", "canja de galinha", "caldo verde")))
        add(listOf("stew"), Entry("ensopado", listOf("feijoada", "estrogonofe de carne", "vaca atolada"), checked = 0))
        add(listOf("potato", "potatoes", "baked potato"), Entry("batata", listOf("batata, inglesa, cozida")))
        add(listOf("french fries", "fries", "potato chip"), Entry("batata frita", listOf("batata, inglesa, frita")))
        add(listOf("mashed potato"), Entry("purê de batata", listOf("batata, inglesa, pure (com leite e manteiga)")))
        add(listOf("corn", "corn on the cob", "sweet corn", "maize"), Entry("milho", listOf("milho verde, cozido (espiga)")))
        add(listOf("carrot"), Entry("cenoura", listOf("cenoura, crua")))
        add(listOf("broccoli"), Entry("brócolis", listOf("brocolis, cozido")))
        add(listOf("tomato", "cherry tomatoes"), Entry("tomate", listOf("tomate, salada")))
        add(listOf("cucumber"), Entry("pepino", listOf("pepino, cru")))
        add(listOf("onion"), Entry("cebola", listOf("cebola, crua")))
        add(listOf("pumpkin", "squash", "winter squash"), Entry("abóbora", listOf("abobora, cabotian, cozida")))
        add(listOf("cabbage"), Entry("repolho", listOf("repolho, branco, cru")))
        add(listOf("spinach"), Entry("espinafre", listOf("espinafre, nova zelandia, refogado")))
        add(listOf("cassava"), Entry("mandioca", listOf("mandioca, cozida")))
        add(listOf("banana"), Entry("banana", listOf("banana, prata, crua")))
        add(listOf("apple"), Entry("maçã", listOf("maca, fuji, com casca, crua")))
        add(listOf("orange", "citrus", "tangerine", "mandarin orange", "clementine"), Entry("laranja", listOf("laranja, pera, crua", "tangerina, ponca, crua")))
        add(listOf("lemon", "lime"), Entry("limão", listOf("limao, tahiti, cru")))
        add(listOf("strawberry", "strawberries", "berry"), Entry("morango", listOf("morango, cru")))
        add(listOf("grape", "grapes"), Entry("uva", listOf("uva, italia, crua")))
        add(listOf("watermelon"), Entry("melancia", listOf("melancia, crua")))
        add(listOf("melon"), Entry("melão", listOf("melao, cru")))
        add(listOf("pineapple"), Entry("abacaxi", listOf("abacaxi, cru")))
        add(listOf("mango"), Entry("manga", listOf("manga, palmer, crua")))
        add(listOf("papaya"), Entry("mamão", listOf("mamao, papaia, cru")))
        add(listOf("avocado"), Entry("abacate", listOf("abacate, cru")))
        add(listOf("pear"), Entry("pera", listOf("pera, williams, crua")))
        add(listOf("peach"), Entry("pêssego", listOf("pessego, aurora, cru")))
        add(listOf("kiwi", "kiwifruit"), Entry("kiwi", listOf("kiwi, cru")))
        add(listOf("coconut"), Entry("coco", listOf("coco, cru")))
        add(listOf("nut", "nuts", "cashew", "almond", "peanut", "walnut"), Entry("castanhas", listOf("castanha-de-caju, torrada, salgada", "amendoim, torrado, salgado")))
        add(listOf("popcorn"), Entry("pipoca", listOf("pipoca, com oleo de soja, sem sal")))
        add(listOf("pancake", "crepe", "flatbread"), Entry("tapioca/crepioca", listOf("tapioca, simples (goma hidratada, sem recheio)", "crepioca (ovo com goma de tapioca)"), checked = 0))
        add(listOf("tortilla", "wrap", "burrito", "taco"), Entry("wrap", listOf("wrap / tortilha de trigo (tipo rap10)")))
        add(listOf("croissant"), Entry("croissant", listOf("croissant")))
        add(listOf("cereal", "granola", "breakfast cereal", "muesli"), Entry("cereal", listOf("granola tradicional")))
        add(listOf("oatmeal", "porridge"), Entry("mingau", listOf("mingau de aveia, com leite e acucar")))
        add(listOf("honey"), Entry("mel", listOf("mel, de abelha")))
        add(listOf("butter"), Entry("manteiga", listOf("manteiga, com sal")))
        add(listOf("pastry", "dumpling", "empanada", "fried food", "deep frying"), Entry("salgado", listOf("pastel, de carne, frito", "coxinha de frango, frita", "esfiha, de carne"), checked = 0))
        add(listOf("tofu"), Entry("tofu", listOf("soja, queijo (tofu)")))
        add(listOf("acai", "acai bowl"), Entry("açaí", listOf("acai na tigela, com granola e banana")))
    }

    /** Palpites a partir dos rótulos (texto, confiança). Rótulos gerais entram sem alimentos. */
    fun guesses(labels: List<Pair<String, Float>>): List<PlateGuess> {
        val out = mutableListOf<PlateGuess>()
        val seen = HashSet<String>()
        labels.sortedByDescending { it.second }.forEach { (text, confidence) ->
            val key = text.trim().lowercase()
            val entry = table[key]
            if (entry != null) {
                if (seen.add(entry.pt)) out += PlateGuess(entry.pt, confidence, entry.foods, entry.checked)
            } else {
                generic[key]?.let { pt -> if (seen.add(pt)) out += PlateGuess(pt, confidence, emptyList(), 0) }
            }
        }
        return out
    }
}
