package com.identifiant.ordoread.data.parser

import android.util.Log
import com.identifiant.ordoread.domain.ai.Medication
import com.identifiant.ordoread.domain.ai.PrescriptionAnalyzer

class LocalPrescriptionAnalyzer : PrescriptionAnalyzer {

    companion object {
        private const val TAG = "OrdoParser"

        private val ignoreRegex = Regex(
            "\\b(docteur|dr\\.?|médecin|medecin|patient|m\\.|mme|melle|monsieur|madame|" +
            "âge|age|né\\(e\\)|né le|née le|nee le|ne le|fait le|" +
            "tél|tel|téléphone|telephone|fax|mail|email|e-mail|portable|mobile|" +
            "signature|ordonnance|prescription|renouvellement|" +
            "ald|sécurité sociale|securite sociale|n°\\s*ss|nss|" +
            "rue|avenue|boulevard|chemin|impasse|allée|allee|place|cedex|code postal|" +
            "ville|adresse|cabinet|clinique|hôpital|hopital|centre|" +
            "rpps|finess|adeli|cpam|mutuelle|" +
            "qare|santé|sante|pharmacie|" +
            "qr code|mot de passe|urgence|contactez|chat|client|destination|" +
            "cdom|scannez|doute|appeler|page|" +
            "diplôme|diplome|université|universite|faculté|faculte|" +
            "remplaçant|remplacant|spécialiste|specialiste|généraliste|generaliste|" +
            "consultation|rendez-vous|rdv|" +
            "identifiant|numéro|numero|" +
            "bonjour|cordialement|merci|" +
            "inscrit|tableau|ordre|conseil|" +
            "spécialité|specialite|prescrite|" +
            "paris|lyon|marseille|toulouse|nice|nantes|strasbourg|montpellier|bordeaux|lille|" +
            "protégées|droits d'auteur|savoir plus|accéder|site|images peuvent|en ligne)\\b",
            RegexOption.IGNORE_CASE
        )

        private val dateRegex = Regex("\\b\\d{1,2}[/\\-.]\\d{1,2}[/\\-.]\\d{2,4}\\b")
        private val longNumberRegex = Regex("\\b\\d{7,}\\b")
        private val emailUrlRegex = Regex(
            "[a-zA-Z0-9._%+\\-]+@[a-zA-Z0-9.\\-]+\\.[a-zA-Z]{2,}|www\\.|http",
            RegexOption.IGNORE_CASE
        )
        private val paginationRegex = Regex("^\\d+\\s*/\\s*\\d+$")
        private val onlyDigitsAndPunctuation = Regex("^[\\d\\s.,;:/\\-+()]+$")
        private val urlLikeRegex = Regex("[a-z\\-]+/[a-z\\-]+/[a-z\\-]+", RegexOption.IGNORE_CASE)

        /** Noms de médicaments courants pour détecter même sans indicateur pharma */
        private val knownDrugNames = Regex(
            "\\b(doliprane|efferalgan|dafalgan|paracetamol|paracétamol|ibuprofène|ibuprofene|ibuprofen|" +
            "advil|nurofen|aspirine|aspegic|aspirin|" +
            // Antibiotiques
            "amoxicilline|augmentin|clamoxyl|orelox|cefpodoxime|" +
            "azithromycine|zithromax|" +
            "ciprofloxacine|ciflox|ofloxacine|oflocet|" +
            "doxycycline|tolexine|granudoxy|" +
            "pristinamycine|pyostacine|" +
            "cotrimoxazole|bactrim|" +
            "amoxiclav|co-amoxicilline|" +
            "ceftriaxone|rocephine|" +
            "norfloxacine|noroxine|" +
            "nitrofurantoine|furadantine|" +
            "fosfomycine|monuril|" +
            "spiramycine|birodogyl|flagyl|metronidazole|" +
            // Diabète
            "metformine|glucophage|stagid|" +
            "diamicron|gliclazide|januvia|sitagliptine|" +
            "jardiance|empagliflozine|forxiga|dapagliflozine|" +
            "ozempic|semaglutide|trulicity|dulaglutide|victoza|liraglutide|" +
            "insuline|lantus|novorapid|humalog|levemir|toujeo|" +
            "glimepiride|amarel|" +
            // Gastro / estomac
            "omeprazole|mopral|inexium|esomeprazole|pantoprazole|rabeprazole|pariet|" +
            "ogast|lansoprazole|lanzor|" +
            "gaviscon|smecta|spasfon|debridat|forlax|movicol|duphalac|" +
            "domperidone|motilium|" +
            "loperamide|imodium|" +
            "phosphalugel|maalox|" +
            // Thyroïde
            "levothyrox|euthyrox|l-thyroxine|tcaps|" +
            // Respiratoire
            "ventoline|salbutamol|seretide|symbicort|flixotide|" +
            "singulair|montelukast|" +
            "atrovent|ipratropium|spiriva|tiotropium|" +
            "pulmicort|budesonide|" +
            "aerius|zyrtec|cetirizine|desloratadine|bilastine|" +
            "toplexil|humex|tussidane|helicidine|" +
            "rhinofluimucil|pivalone|nasonex|avamys|" +
            // Douleur / anti-inflammatoires
            "codeine|codéine|neo-codion|neocodion|lamaline|" +
            "tramadol|ixprim|topalgic|contramal|" +
            "voltarene|ketoprofene|flector|diclofenac|" +
            "celecoxib|celebrex|" +
            "nefopam|acupan|" +
            "morphine|oxycodone|oxynorm|oxycontin|skenan|actiskenan|" +
            "palier|diantalvic|" +
            // Corticoïdes
            "prednisolone|solupred|cortancyl|celestene|" +
            "prednisone|medrol|methylprednisolone|" +
            "dexamethasone|" +
            "betamethasone|diprostene|" +
            // Cardio / tension
            "kardegic|plavix|eliquis|xarelto|pradaxa|" +
            "tahor|crestor|atorvastatine|rosuvastatine|simvastatine|zocor|" +
            "amlodipine|ramipril|bisoprolol|perindopril|valsartan|" +
            "coversyl|triatec|" +
            "lasilix|furosemide|" +
            "previscan|coumadine|warfarine|" +
            "atenolol|tenormine|" +
            "losartan|cozaar|candesartan|atacand|irbesartan|aprovel|" +
            "spironolactone|aldactone|" +
            "nebivolol|temerit|nébilox|" +
            "diltiazem|tildiem|" +
            "flecainide|flecaine|" +
            "daflon|veinotonique|" +
            // Anticoagulants / antiagrégants
            "lovenox|enoxaparine|innohep|tinzaparine|" +
            "clopidogrel|" +
            // Psychotropes / neuro
            "lorazepam|lexomil|xanax|alprazolam|bromazepam|" +
            "zopiclone|stilnox|zolpidem|imovane|" +
            "lyrica|pregabaline|gabapentine|" +
            "seroplex|escitalopram|prozac|fluoxetine|deroxat|paroxetine|" +
            "laroxyl|amitriptyline|" +
            "venlafaxine|effexor|" +
            "duloxetine|cymbalta|" +
            "mirtazapine|norset|" +
            "sertraline|zoloft|" +
            "olanzapine|zyprexa|" +
            "risperidone|risperdal|" +
            "quetiapine|xeroquel|" +
            "aripiprazole|abilify|" +
            "valproate|depakine|depakote|" +
            "carbamazepine|tegretol|" +
            "lamotrigine|lamictal|" +
            "levetiracetam|keppra|" +
            "clonazepam|rivotril|" +
            "donepezil|aricept|" +
            "melatonine|circadin|" +
            // Urologie / prostate
            "tamsulosine|omix|josir|" +
            "alfuzosine|xatral|" +
            "finasteride|chibro-proscar|" +
            "sildenafil|viagra|tadalafil|cialis|" +
            // Ophtalmologie
            "travoprost|travatan|" +
            "timolol|timoptol|" +
            "latanoprost|xalatan|" +
            // Dermatologie
            "fucidine|acide fusidique|" +
            "dermoval|betnesol|" +
            "dexeryl|cicatryl|" +
            "ketoderm|econazole|" +
            // Vitamines / suppléments
            "uvedose|vitamine d|zymad|adrigyl|" +
            "vitamine b12|hydroxocobalamine|" +
            "vitamine c|" +
            "speciafoldine|acide folique|tardyferon|fer|ferrostrane|" +
            "magne b6|magnesium|mag2|" +
            "calcium|cacit|orocal|" +
            "potassium|diffu-k|kaleorid|" +
            // Divers
            "maxilase|alpha-amylase|" +
            "colchicine|colchimax|" +
            "allopurinol|zyloric|" +
            "methotrexate|novatrex|" +
            "hydroxychloroquine|plaquenil)\\b",
            RegexOption.IGNORE_CASE
        )

        /** Filtre positif : indicateur pharmaceutique */
        private val medicationIndicator = Regex(
            "\\b(mg|ml|cl|µg|ug|ui|" +
            "comprimé|comprime|comprimés|comprimes|comp\\b|cp\\b|cpr\\b|" +
            "gélule|gelule|gélules|gelules|gél\\b|gel\\b|" +
            "sachet|sachets|" +
            "sirop|srop|solution|suspension|émulsion|emulsion|buvable|" +
            "goutte|gouttes|gtte|gttes|" +
            "suppositoire|suppositoires|suppo|" +
            "crème|creme|pommade|lotion|" +
            "collyre|" +
            "injectable|injection|ampoule|ampoules|amp\\b|" +
            "patch|patchs|dispositif|" +
            "spray|aérosol|aerosol|inhalateur|" +
            "ovule|ovules|" +
            "capsule|capsules|" +
            "poudre|lyophilisat|" +
            "flacon|tube|stylo|seringue|" +
            "\\d+\\s*mg|\\d+\\s*g\\b|\\d+\\s*ml|\\d+\\s*µg|\\d+\\s*ui|\\d+\\s*%)" +
            "",
            RegexOption.IGNORE_CASE
        )

        /** Détecte une ligne d'instruction de prise (commence par quantité + forme galénique) */
        private val posologyLineRegex = Regex(
            "^[1-9il]\\s*(?:\\([^)]*\\)\\s*)?(à\\s*\\d\\s*)?(cuill\\w*|gélule|gelule|gélules|gelules|" +
            "comprimé|comprime|comprimés|comprimes|comp\\b|cp\\b|cpr\\b|" +
            "sachet|sachets|goutte|gouttes|gtte|" +
            "suppositoire|suppositoires|suppo|" +
            "injection|ampoule|dose|mesure|" +
            "application|pulvérisation|pulverisation|bouffée|bouffee|" +
            "capsule|capsules|ovule|ovules)\\b",
            RegexOption.IGNORE_CASE
        )

        /** Détecte aussi les lignes qui commencent par un horaire ou fréquence */
        private val scheduleLineRegex = Regex(
            "^(matin|midi|soir|coucher|le matin|le soir|au coucher|" +
            "\\d+\\s*fois|une fois|deux fois|trois fois|quatre fois|" +
            "toutes les|tous les|" +
            "pendant|durant|" +
            "prendre|avaler|appliquer|" +
            "à jeun|a jeun|avant repas|après repas|apres repas|au repas|pendant repas|" +
            "si besoin|en cas de|si douleur|si fièvre|si fievre|si nécessaire|si necessaire)\\b",
            RegexOption.IGNORE_CASE
        )

        /** Détecte une instruction combinée (posologie + horaire sur une seule ligne) */
        private val combinedPosologyRegex = Regex(
            "\\d\\s*(cp|cpr|comp|comprimé|comprime|gél|gelule|gélule|sachet|" +
            "cuill\\w*|goutte|gtte|dose|bouffée|bouffee|mesure|application)\\w*" +
            "\\s*(le\\s+)?(matin|midi|soir|coucher)",
            RegexOption.IGNORE_CASE
        )

        /** Horaires en texte dans une ligne (pour extraction inline) */
        private val inlineScheduleRegex = Regex(
            "\\b(matin\\b.*soir|soir\\b.*matin|" +
            "matin\\b.*midi\\b.*soir|" +
            "matin\\s*,\\s*midi\\s*(,|et)\\s*soir|" +
            "matin\\s*(,|et)\\s*soir|" +
            "midi\\s*(,|et)\\s*soir|" +
            "matin\\s*,\\s*midi)",
            RegexOption.IGNORE_CASE
        )

        private val intervalRegex = Regex(
            "(toutes les|tous les|ttes les|espac\\w+ de|minimum|au moins|intervalle de|attendre)\\s*(\\d+)\\s*(h|heure|heures)\\b",
            RegexOption.IGNORE_CASE
        )
        private val intervalAltRegex = Regex(
            "(\\d+)\\s*(h|heure|heures)\\s*(entre|d'intervalle|d'écart|d'espacement|minimum|d'espac)",
            RegexOption.IGNORE_CASE
        )

        private val durationRegex = Regex(
            "(pendant|durant|pour|sur)\\s+(\\d+)\\s*(?:\\([^)]*\\)\\s*)?(jour|jours|j\\b|semaine|semaines|mois)",
            RegexOption.IGNORE_CASE
        )
        private val durationStandaloneRegex = Regex(
            "\\b(\\d+)\\s*(?:\\([^)]*\\)\\s*)?(jour|jours|j\\b|semaine|semaines|mois)\\b",
            RegexOption.IGNORE_CASE
        )

        private val frequencyRegex = Regex(
            "(\\d+)\\s*(à|a)\\s*(\\d+)\\s*fois|" +
            "(\\d+)\\s*fois\\s*(par jour|par jours|/\\s*jour|/\\s*j)|" +
            "(une|deux|trois|quatre)\\s*fois\\s*(par jour|/\\s*jour|/\\s*j)|" +
            "(\\d+)\\s*x\\s*/\\s*(jour|j)|" +
            "(\\d+)\\s*fois\\s*/\\s*(jour|j)|" +
            "(\\d+)\\s*par\\s*jour|" +
            "(\\d+)\\s*(à|a)\\s*(\\d+)\\s*\\w{0,4}\\s*par\\s*jour",
            RegexOption.IGNORE_CASE
        )

        // Nettoie "1) ", "2. ", "I) ", "II) " etc. en début de ligne
        private val numberingPrefix = Regex("^[\\dIiVvXx]+[).:]\\s*")

        private val formToMedKeywords = mapOf(
            "cuill" to listOf("sirop", "srop", "solution", "suspension", "flacon", "buvable", "ml"),
            "gélule" to listOf("gél", "gélule", "gelule", "capsule", "gélules", "gelules"),
            "gelule" to listOf("gél", "gélule", "gelule", "capsule", "gélules", "gelules"),
            "comprimé" to listOf("comprimé", "comprime", "comp", "cp", "cpr", "mg"),
            "comprime" to listOf("comprimé", "comprime", "comp", "cp", "cpr", "mg"),
            "cp" to listOf("comprimé", "comprime", "comp", "cp", "cpr", "mg"),
            "cpr" to listOf("comprimé", "comprime", "comp", "cp", "cpr", "mg"),
            "sachet" to listOf("sachet", "poudre", "sachets"),
            "goutte" to listOf("collyre", "goutte", "flacon", "gouttes"),
            "gtte" to listOf("collyre", "goutte", "flacon", "gouttes"),
            "suppositoire" to listOf("suppositoire", "suppo"),
            "injection" to listOf("injectable", "injection", "ampoule", "seringue", "stylo"),
            "ampoule" to listOf("injectable", "injection", "ampoule"),
            "application" to listOf("crème", "creme", "pommade", "gel", "lotion", "patch", "tube"),
            "bouffée" to listOf("spray", "aérosol", "aerosol", "inhalateur", "ventoline"),
            "bouffee" to listOf("spray", "aérosol", "aerosol", "inhalateur", "ventoline"),
            "dose" to listOf("spray", "aérosol", "aerosol", "inhalateur", "solution", "buvable"),
            "mesure" to listOf("sirop", "solution", "suspension", "buvable", "ml")
        )

        private val wordToNumber = mapOf(
            "une" to 1, "un" to 1, "deux" to 2, "trois" to 3, "quatre" to 4
        )
    }

    private data class MedBuilder(
        val name: String,
        val lineIndex: Int,
        var hours: MutableList<Int> = mutableListOf(),
        var durationDays: Int = 7,
        var isPriseLibre: Boolean = false,
        var intervalHours: Int = 0,
        var hasExplicitDuration: Boolean = false
    )

    override suspend fun analyzeText(extractedText: String): List<Medication> {
        Log.d(TAG, "=== ANALYSE REGEX ===")

        // Correction OCR avant parsing
        val correctedText = fixOcrErrors(extractedText)
        val lines = correctedText.lines().map { it.trim() }.filter { it.isNotEmpty() }

        // Log toutes les lignes pour debug
        lines.forEachIndexed { i, l -> Log.d(TAG, "  [$i] $l") }

        // === PASSE 1 : identifier les médicaments et les lignes d'instruction ===
        val medBuilders = mutableListOf<MedBuilder>()
        val instructionLines = mutableListOf<Pair<Int, String>>() // index + texte

        for ((index, line) in lines.withIndex()) {
            val lower = line.lowercase()

            if (shouldIgnoreLine(lower, line)) continue

            val isPosologyStart = posologyLineRegex.containsMatchIn(lower)
            val isScheduleStart = scheduleLineRegex.containsMatchIn(lower)
            val hasMedIndicator = medicationIndicator.containsMatchIn(lower)
            val hasKnownDrug = knownDrugNames.containsMatchIn(lower)
            val hasCombinedPosology = combinedPosologyRegex.containsMatchIn(lower)

            // Une ligne est un médicament si elle a un indicateur pharma OU un nom connu
            val hasMedSignal = hasMedIndicator || hasKnownDrug

            // Déterminer si la ligne contient un VRAI nom de médicament (pas juste "gélule" ou "comprimé")
            // Une ligne purement posologique ("1 gélule toutes les 6h") a un indicateur pharma
            // mais pas de nom de médicament → c'est une instruction, pas un médicament
            val hasActualDrugName = hasKnownDrug || hasDrugNamePattern(lower)

            // Cas 1 : Ligne médicament pure (pas une instruction qui commence la ligne)
            val isMedicationLine = hasMedSignal && hasActualDrugName && !isPosologyStart && !isScheduleStart

            if (isMedicationLine) {
                val cleanName = cleanMedicationName(numberingPrefix.replace(line, "").trim())
                val builder = MedBuilder(cleanName, index)
                extractInlineInfo(lower, builder)
                // Extraire horaires inline si la ligne contient "matin", "soir", etc.
                extractInlineSchedule(lower, builder)
                medBuilders.add(builder)
                Log.d(TAG, "  MED[$index]: '$cleanName' hours=${builder.hours}")
            } else if (hasMedSignal && hasKnownDrug && (isPosologyStart || hasCombinedPosology)) {
                // Ligne mixte : médicament + instruction sur la même ligne
                // Ex: "1 comprimé de Doliprane 1g matin et soir"
                // Exige un nom de médicament CONNU (pas juste un pattern) pour éviter
                // "1 (un) comprimé le matin..." qui est une pure instruction
                val cleanName = cleanMedicationName(numberingPrefix.replace(line, "").trim())
                val builder = MedBuilder(cleanName, index)
                extractInlineInfo(lower, builder)
                extractInlineSchedule(lower, builder)
                applyInstruction(lower, builder)
                medBuilders.add(builder)
                Log.d(TAG, "  MED+INSTR[$index]: '$cleanName' hours=${builder.hours}")
            } else if (isPosologyStart || isScheduleStart || hasCombinedPosology || hasMedIndicator ||
                lower.contains("fois") || lower.contains("pendant") ||
                lower.contains("par jour") || lower.contains("par j") ||
                lower.contains("cuillère") || lower.contains("cuill") ||
                lower.contains("matin") || lower.contains("soir") || lower.contains("midi") ||
                lower.contains("douleur") || lower.contains("fièvre") || lower.contains("besoin") ||
                lower.contains("heures") || lower.contains("toutes les")) {
                instructionLines.add(index to lower)
            }
        }

        // === PASSE 2 : rattacher instructions aux médicaments ===
        for ((instrIndex, instrLine) in instructionLines) {
            val target = findTargetMedication(instrLine, instrIndex, medBuilders)
            if (target != null) {
                applyInstruction(instrLine, target)
                Log.d(TAG, "  INSTR[$instrIndex] -> '${target.name}'")
            }
        }

        // === Construire le résultat ===
        val medications = medBuilders.map { builder ->
            if (builder.isPriseLibre) {
                Medication(
                    description = builder.name,
                    hours = emptyList(),
                    durationDays = builder.durationDays,
                    isPriseLibre = true,
                    intervalHours = builder.intervalHours,
                    addToCalendar = false
                )
            } else {
                if (builder.hours.isEmpty()) builder.hours.add(8)
                Medication(
                    description = builder.name,
                    hours = builder.hours.distinct().sorted(),
                    durationDays = builder.durationDays,
                    isPriseLibre = false,
                    intervalHours = builder.intervalHours
                )
            }
        }

        Log.d(TAG, "=== RÉSULTAT: ${medications.size} médicaments ===")
        medications.forEach {
            Log.d(TAG, "  -> ${it.description} | hours=${it.hours} | ${it.durationDays}j | libre=${it.isPriseLibre}")
        }

        return medications
    }

    /** Nettoie le nom d'un médicament extrait */
    private fun cleanMedicationName(name: String): String {
        var clean = name
        // Retirer ponctuation parasite en début/fin
        clean = clean.trimStart(')', '(', '.', ':', ',', ';', '+', '-', ' ')
        clean = clean.trimEnd(':', '.', ',', ';', ' ')
        // Retirer "I " ou "T " ou "1 " ou "0 " parasite en début (artefact OCR de numérotation)
        clean = clean.replace(Regex("^[IT10]\\s+(?=[A-Z])"), "")
        // Retirer ":" et "." et ";" au milieu qui sont des artefacts OCR
        clean = clean.replace(Regex("\\s*[;:]\\s*"), " ")
        clean = clean.replace(Regex("(?<=\\w)\\.\\s+"), " ")
        // "Ig" / "lg" / " l " isolé devant forme galénique → "1g" / "1"
        clean = clean.replace(Regex("\\b[Il]g\\b"), "1g")
        clean = clean.replace(Regex("\\bl\\s+(gél|gel|g\\b|mg|ml|cp|comp)"), "1 $1")
        // "géI" (I majuscule à la fin) → "gél" (OCR confond l/I)
        clean = clean.replace(Regex("gé[Il]$"), "gélule")
        clean = clean.replace(Regex("gé[Il]\\b"), "gélule")
        // "srop" / "siop" collé au mot précédent → " sirop"
        clean = clean.replace(Regex("(\\w)(?:srop|siop)"), "$1 sirop")
        clean = clean.replace(Regex("\\bsiop\\b"), "sirop")
        // "I80ml" → "180ml"
        clean = clean.replace(Regex("\\bI(\\d+ml)\\b"), "1$1")
        // Retirer virgule entre mots
        clean = clean.replace(Regex(",\\s*"), " ")
        // Nettoyer espaces multiples
        clean = clean.replace(Regex("\\s+"), " ").trim()
        return clean
    }

    /** Corrige les erreurs OCR courantes — très agressif pour maximiser la détection */
    private fun fixOcrErrors(text: String): String {
        var result = text

        // === Corrections de chiffres ===
        // I ou l devant chiffres+unité → 1 (ex: "I80ml" → "180ml", "lg" → "1g")
        result = Regex("\\b[Il](\\d+\\s*(?:mg|g|ml|µg|ui|%))").replace(result) { "1${it.groupValues[1]}" }
        // O entre chiffres → 0 (ex: "1O0mg" → "100mg")
        result = Regex("(\\d)O(\\d)").replace(result) { "${it.groupValues[1]}0${it.groupValues[2]}" }
        result = Regex("(\\d)O(\\s*(?:mg|g|ml|µg|ui|%))").replace(result) { "${it.groupValues[1]}0${it.groupValues[2]}" }
        // "s" devant chiffres+unité → "5" (ex: "s00 mg" → "500 mg")
        result = Regex("\\bs(\\d+\\s*(?:mg|g|ml|µg|ui|%))").replace(result) { "5${it.groupValues[1]}" }
        // "lg" / "Ig" isolé → "1g"
        result = result.replace(Regex("\\b[lI]g\\b"), "1g")

        // === Corrections d'unités ===
        // "ng" → "mg" quand précédé de chiffres en contexte pharma (500 ng/30 mg → 500 mg/30 mg)
        result = Regex("(\\d+)\\s*ng\\b").replace(result) { "${it.groupValues[1]} mg" }
        // "comprim" tronqué en fin de ligne ou devant un espace → "comprimé"
        result = result.replace(Regex("\\bcomprim\\b", RegexOption.IGNORE_CASE), "comprimé")
        // "mg30" → "mg/30" (dosages collés, séparateur manquant)
        result = Regex("(mg)(\\d)").replace(result) { "${it.groupValues[1]}/${it.groupValues[2]}" }

        // === Corrections de mots médicaux ===
        result = result.replace(Regex("\\bsrop\\b", RegexOption.IGNORE_CASE), "sirop")
        result = result.replace(Regex("\\bsop\\b", RegexOption.IGNORE_CASE), "sirop")
        result = result.replace(Regex("\\bgelule\\b", RegexOption.IGNORE_CASE), "gélule")
        // "Teuale a soupe" / "cuillere a soupe" / variations OCR → "cuillère à soupe"
        result = Regex("\\b[Tt][eéè]?[ua]l?[eéè]?s?\\s*[aà]\\s*soupe\\b", RegexOption.IGNORE_CASE)
            .replace(result, "cuillère à soupe")
        result = Regex("\\bcuill?[eèéê]?r?[eé]?s?\\s*[aà]\\s*(?:soupe|café|cafe)\\b", RegexOption.IGNORE_CASE)
            .replace(result) { "cuillère à ${if (it.value.contains("café") || it.value.contains("cafe")) "café" else "soupe"}" }
        // "eodion" → "codion" (Neo-Codion)
        result = result.replace(Regex("\\b[Nn]eo[- ]?[ec]odion\\b", RegexOption.IGNORE_CASE), "Neo-Codion")
        // "PARACETAMIOL" / "Paracátamal" / "paracetanol" → "Paracétamol"
        result = result.replace(Regex("\\bparac[eéèáa]t[aá]m[oai]+l\\b", RegexOption.IGNORE_CASE), "Paracétamol")
        // "codäine" / "codeine" / "codéine" / "cadálne" / "cadaine" → "codéine"
        result = result.replace(Regex("\\bc[oa]d[eéèäaá][iïl]ne\\b", RegexOption.IGNORE_CASE), "codéine")
        // "camprmé" / "com primé" / "comprimd" → "comprimé"
        result = result.replace(Regex("\\bcam?pr[mi]m?[eéd]\\b", RegexOption.IGNORE_CASE), "comprimé")
        result = result.replace(Regex("\\bcom\\s+prim[eéd]\\b", RegexOption.IGNORE_CASE), "comprimé")
        result = result.replace(Regex("\\bcomprimd\\b", RegexOption.IGNORE_CASE), "comprimé")

        // Noms de médicaments courants mal lus par OCR
        // Doliprane
        result = result.replace(Regex("\\b[Dd][oö]l[il]?[pb]r[aà]ne\\b"), "Doliprane")
        result = result.replace(Regex("\\b[Dd]ol[il]pane\\b"), "Doliprane")
        // Augmentin
        result = result.replace(Regex("\\b[Aa]ugm[ea]nt[il]n\\b"), "Augmentin")
        // Amoxicilline
        result = result.replace(Regex("\\b[Aa]m[oa]x[il]c[il]{1,2}[il]ne\\b"), "Amoxicilline")
        // Levothyrox
        result = result.replace(Regex("\\b[Ll][eè]v[oa]th[yiï]r[oa]x\\b"), "Levothyrox")
        // Ventoline
        result = result.replace(Regex("\\b[Vv][ea]nt[oa]l[il]ne\\b"), "Ventoline")
        // Spasfon
        result = result.replace(Regex("\\b[Ss]p[aâ]sf[oa]n\\b"), "Spasfon")
        // Tramadol
        result = result.replace(Regex("\\b[Tt]r[aà]m[aà]d[oö]l\\b"), "Tramadol")
        // Lexomil
        result = result.replace(Regex("\\b[Ll][eé]x[oa]m[il]l\\b"), "Lexomil")
        // Solupred
        result = result.replace(Regex("\\b[Ss][oö]l[uü]pr[eéè]d\\b"), "Solupred")
        // Kardegic
        result = result.replace(Regex("\\b[Kk][aà]rd[eé]g[il]c\\b"), "Kardegic")
        // Gaviscon
        result = result.replace(Regex("\\b[Gg][aà]v[il]sc[oa]n\\b"), "Gaviscon")
        // Seroplex
        result = result.replace(Regex("\\b[Ss][eé]r[oa]pl[eé]x\\b"), "Seroplex")

        // === Corrections de fréquence ===
        // "I a4 fois" / "I a 4 fois" / "1 a4 fois" → "1 a 4 fois"
        result = Regex("[Il1]\\s*[aà]\\s*(\\d)\\s*fois", RegexOption.IGNORE_CASE)
            .replace(result) { "1 a ${it.groupValues[1]} fois" }
        // "4 fois par jours" → "4 fois par jour"
        result = result.replace(Regex("\\bpar jours\\b", RegexOption.IGNORE_CASE), "par jour")
        // "lpar gours" / "1par jour" / "Ipar jour" → "1 par jour"
        result = Regex("[lI1]\\s*par\\s*(?:gours?|jours?)\\b", RegexOption.IGNORE_CASE)
            .replace(result, "1 par jour")
        // "Xpar jour" → "X par jour"
        result = Regex("(\\d)par\\s*(?:gours?|jours?)\\b", RegexOption.IGNORE_CASE)
            .replace(result) { "${it.groupValues[1]} par jour" }
        // "gours" / "gour" → "jour" (OCR confond j/g)
        result = result.replace(Regex("\\bgours?\\b", RegexOption.IGNORE_CASE), "jour")
        // "Tjours" → "7 jours" (OCR confond T/7)
        result = Regex("\\bT(\\s*jours?)\\b", RegexOption.IGNORE_CASE)
            .replace(result) { "7${it.groupValues[1]}" }

        // === Corrections de durée ===
        // "pendam" "pendkant" "pendanr" "pendane" "pendanl" → "pendant"
        result = result.replace(Regex("\\bpend[a-z]{1,4}[a-z]\\b", RegexOption.IGNORE_CASE), "pendant")
        // "IO jours" "IO ors" "10 ors" → "10 jours" (avec OCR garbage sur "jours")
        result = Regex("[Il1]([O0])\\s*(?:jour|jours|ors|ours|jrs|j)\\b", RegexOption.IGNORE_CASE)
            .replace(result) { "10 jours" }
        // "I5 jours" "I4 jours" → "15 jours" "14 jours"
        result = Regex("[Il](\\d)\\s*(jour|jours|ors|ours|jrs)\\b", RegexOption.IGNORE_CASE)
            .replace(result) { "1${it.groupValues[1]} jours" }
        // "X semaine" avec OCR
        result = Regex("[Il](\\d)\\s*(semaine|semaines|sem)\\b", RegexOption.IGNORE_CASE)
            .replace(result) { "1${it.groupValues[1]} semaines" }

        // === Corrections d'intervalle ===
        // "aues les" / "outes les" / "ttes les" / "ous les" → "toutes les"
        result = Regex("\\b[a-z]*(?:ou|u|au)(?:te)?s?\\s*[Ll]es\\s*(\\d+)\\s*(?:heure|heures|heuses|h|bourES|beure|beures|heres|hs)\\b", RegexOption.IGNORE_CASE)
            .replace(result) { "toutes les ${it.groupValues[1]} heures" }
        // "heuses" / "beures" / "beure" / "heres" → "heures" (OCR confusions)
        result = result.replace(Regex("\\b(?:heuses|beures?|heres)\\b", RegexOption.IGNORE_CASE), "heures")

        // === Corrections de prise libre ===
        // "nevre" "ievre" "fievre" → "fièvre"
        result = result.replace(Regex("\\b[nif]?[eèi][eè]?vre\\b", RegexOption.IGNORE_CASE), "fièvre")
        // "fee" / "fée" isolé en contexte "douleur ou fee" → "fièvre"
        result = result.replace(Regex("\\b(ou|et)\\s+f[eéè]+\\b", RegexOption.IGNORE_CASE)) { "${it.groupValues[1]} fièvre" }
        // "douleus" "douleu" "douieur" → "douleur"
        result = result.replace(Regex("\\bdoul[eéi][eu][urs]*\\b", RegexOption.IGNORE_CASE), "douleur")
        // "pélule" "pelule" → "gélule"
        result = result.replace(Regex("\\bp[eéè]lules?\\b", RegexOption.IGNORE_CASE), "gélule")
        // "ai douleur" "si douleur" — "ai" est souvent OCR pour "si"
        result = result.replace(Regex("\\bai\\s+(douleur|fièvre|besoin|nécessaire)", RegexOption.IGNORE_CASE)) { "si ${it.groupValues[1]}" }

        // === Corrections de posologie ===
        // "comprimé" mal orthographié : "comprlmé", "comprimŕ", "comprîmé", "comprimné"
        result = result.replace(Regex("\\bcompr[iîíl]m[neéèêŕ][eé]?s?\\b", RegexOption.IGNORE_CASE), "comprimé")
        // "gélule" mal orthographié
        result = result.replace(Regex("\\bg[eéè]l[uü]les?\\b", RegexOption.IGNORE_CASE), "gélule")
        // "cuillère" avec OCR
        result = result.replace(Regex("\\bcuill[eèéê]r[eé]s?\\b", RegexOption.IGNORE_CASE), "cuillère")
        // "mafin" / "mahn" → "matin"
        result = result.replace(Regex("\\bma[ft][il]n\\b", RegexOption.IGNORE_CASE), "matin")
        // "solr" / "sojr" / "sor" → "soir"
        result = result.replace(Regex("\\bso[ijl]?r\\b", RegexOption.IGNORE_CASE), "soir")
        // "mid" isolé en début/contexte horaire → "midi"
        result = result.replace(Regex("\\bmid[,.]?\\s", RegexOption.IGNORE_CASE)) { "midi " }

        // === Nettoyage général ===
        // Multiples espaces → un seul
        result = result.replace(Regex("\\s{2,}"), " ")

        return result
    }

    private fun shouldIgnoreLine(lower: String, original: String): Boolean {
        if (lower.length <= 2) return true
        if (onlyDigitsAndPunctuation.matches(lower)) return true
        if (ignoreRegex.containsMatchIn(lower)) return true
        if (dateRegex.containsMatchIn(lower)) return true
        if (longNumberRegex.containsMatchIn(lower)) return true
        if (emailUrlRegex.containsMatchIn(lower)) return true
        if (paginationRegex.matches(lower)) return true
        if (urlLikeRegex.containsMatchIn(lower)) return true
        // Ignorer les lignes très courtes sans indicateur médical
        if (lower.length <= 4 && !medicationIndicator.containsMatchIn(lower)) return true
        return false
    }

    /**
     * Vérifie qu'une ligne contient un vrai nom de médicament (pas juste une forme galénique).
     * "1 gélule toutes les 6h" → false (pas de nom de médicament)
     * "Paracétamol 1g gélule" → true (contient un dosage avec nom)
     * "Neo-Codion Adulte sirop 180ml" → true (contient un nom propre + dosage)
     */
    private fun hasDrugNamePattern(lower: String): Boolean {
        // Un nom de médicament a typiquement : un mot commençant par une majuscule OU
        // un dosage (nombre+unité) associé à un mot qui n'est pas une forme galénique pure
        // Pattern : mot d'au moins 4 lettres suivi d'un dosage (ex: "paracétamol 1g", "neo-codion 180ml")
        val hasDosageWithName = Regex(
            "[a-zéèêàùâîôûç]{4,}\\s+\\d+\\s*(mg|g|ml|µg|ui|%)|" +
            "\\d+\\s*(mg|g|ml|µg|ui|%)\\s+[a-zéèêàùâîôûç]{4,}",
            RegexOption.IGNORE_CASE
        ).containsMatchIn(lower)
        if (hasDosageWithName) return true

        // Mot avec majuscule suivi de forme galénique (ex: "Neo-Codion sirop", "Doliprane comprimé")
        val hasProperNounWithForm = Regex(
            "[A-ZÉÈ][a-zéèêàùâîôûç-]{2,}\\s+.*(sirop|comprimé|gélule|sachet|solution|suspension|collyre|spray|crème|pommade)",
            RegexOption.IGNORE_CASE
        ).containsMatchIn(lower)
        if (hasProperNounWithForm) return true

        // Contient un dosage type "500mg" ou "1g" qui indique un médicament nommé
        // Mais seulement si la ligne n'est pas purement "1 gélule..." ou "2 comprimés..."
        val startsWithQuantityAndForm = Regex(
            "^[1-9il]\\s*(?:\\([^)]*\\)\\s*)?(gélule|gelule|comprimé|comprime|cp|cpr|cuill|sachet|goutte|suppositoire|injection|ampoule|dose|bouffée|capsule|ovule)",
            RegexOption.IGNORE_CASE
        ).containsMatchIn(lower)
        if (startsWithQuantityAndForm) return false

        // A un dosage numérique (500mg, 1g, 180ml) → probablement un médicament
        // Mais rejeter les lignes qui ne sont QUE des dosages (ex: "mg 30 mg comprimé")
        val hasDosage = Regex("\\d+\\s*(mg|g\\b|ml|µg|ui)").containsMatchIn(lower)
        if (!hasDosage) return false

        // Vérifier qu'il y a au moins un mot de 4+ lettres qui n'est PAS une unité ou forme galénique
        val nonMedicalWords = lower.replace(Regex("\\d+"), "")
            .split(Regex("\\s+"))
            .filter { word ->
                word.length >= 4 &&
                !word.matches(Regex("comprimé|comprime|gélule|gelule|sachet|sirop|solution|suspension|goutte|capsule|spray|crème|pommade|collyre|suppositoire|injectable|ampoule|patch|ovule|poudre|flacon|tube|buvable|pendant|jours?|semaine|heures?|fois"))
            }
        return nonMedicalWords.isNotEmpty()
    }

    /** Extrait les horaires nommés s'ils sont sur la même ligne que le médicament */
    private fun extractInlineSchedule(lower: String, target: MedBuilder) {
        // Ne pas écraser si déjà rempli par extractFrequency
        if (target.hours.isNotEmpty()) return

        // Patterns combinés : "matin, midi et soir", "matin et soir", etc.
        val hasMatin = Regex("\\bmatin\\b").containsMatchIn(lower)
        val hasMidi = Regex("\\bmidi\\b").containsMatchIn(lower)
        val hasApresMidi = Regex("\\baprès[- ]?midi\\b|\\bapres[- ]?midi\\b").containsMatchIn(lower)
        val hasSoir = Regex("\\bsoir\\b").containsMatchIn(lower)
        val hasCoucher = Regex("\\bcoucher\\b").containsMatchIn(lower)

        if (hasMatin) target.hours.add(8)
        if (hasMidi) target.hours.add(12)
        if (hasApresMidi) target.hours.add(16)
        if (hasSoir) target.hours.add(20)
        if (hasCoucher) target.hours.add(22)
    }

    /** Extrait durée/fréquence/prise libre directement depuis la ligne du médicament */
    private fun extractInlineInfo(lower: String, target: MedBuilder) {
        // Durée inline
        val durMatch = durationRegex.find(lower) ?: durationStandaloneRegex.find(lower)
        if (durMatch != null) {
            val value = durMatch.groupValues.filter { it.all { c -> c.isDigit() } && it.isNotEmpty() }
                .firstOrNull()?.toIntOrNull()
            val unit = durMatch.groupValues.lastOrNull { it.matches(Regex("jour.*|semaine.*|mois|j")) } ?: ""
            if (value != null) {
                target.durationDays = parseDurationValue(value, unit)
                target.hasExplicitDuration = true
            }
        }

        // Fréquence inline
        extractFrequency(lower, target)

        // Prise libre inline
        if (lower.contains("si besoin") || lower.contains("si douleur") || lower.contains("douleur") ||
            lower.contains("si fièvre") || lower.contains("si fievre") || lower.contains("ievre") ||
            lower.contains("en cas de") || lower.contains("en cas d") ||
            lower.contains("si nécessaire") || lower.contains("si necessaire")) {
            target.isPriseLibre = true
        }

        // Intervalle inline
        extractInterval(lower, target)
    }

    /**
     * Trouve le médicament cible d'une instruction.
     * Stratégie : forme galénique > proximité (la plus proche au-dessus, avec seuil de distance)
     */
    private fun findTargetMedication(instrLine: String, instrIndex: Int, meds: List<MedBuilder>): MedBuilder? {
        if (meds.isEmpty()) return null
        if (meds.size == 1) return meds[0]

        // 1. Match par forme galénique (le plus fiable)
        for ((formKey, medKeywords) in formToMedKeywords) {
            if (instrLine.contains(formKey, ignoreCase = true)) {
                // Chercher le médicament le plus proche qui matche la forme
                val matching = meds
                    .filter { med -> medKeywords.any { med.name.lowercase().contains(it, ignoreCase = true) } }
                    .minByOrNull { kotlin.math.abs(it.lineIndex - instrIndex) }
                if (matching != null) return matching
            }
        }

        // 2. Proximité : le médicament le plus proche au-dessus
        val above = meds.filter { it.lineIndex <= instrIndex }
            .maxByOrNull { it.lineIndex }
        val below = meds.filter { it.lineIndex > instrIndex }
            .minByOrNull { it.lineIndex }

        if (above != null && below != null) {
            val distAbove = instrIndex - above.lineIndex
            val distBelow = below.lineIndex - instrIndex
            // Si l'instruction est clairement "entre" deux médicaments et plus proche du dessous
            if (distBelow <= 1 && distAbove > 3) return below
            // Sinon le médicament au-dessus gagne (convention ordonnance : instructions sous le médicament)
        }

        // 3. Limite de distance : si le médicament au-dessus est trop loin (>5 lignes),
        // il est probable qu'il ne s'agit pas de ce médicament
        if (above != null && (instrIndex - above.lineIndex) > 5) {
            // Vérifier s'il y a un médicament en-dessous plus proche
            if (below != null && (below.lineIndex - instrIndex) <= 2) return below
        }

        return above ?: below
    }

    private fun applyInstruction(lower: String, target: MedBuilder) {
        // Horaires nommés
        val hadHoursBefore = target.hours.toSet()
        if (Regex("\\bmatin\\b").containsMatchIn(lower)) target.hours.add(8)
        if (Regex("\\bmidi\\b").containsMatchIn(lower)) target.hours.add(12)
        if (Regex("\\baprès[- ]?midi\\b|\\bapres[- ]?midi\\b").containsMatchIn(lower)) target.hours.add(16)
        if (Regex("\\bsoir\\b").containsMatchIn(lower)) target.hours.add(20)
        if (Regex("\\bcoucher\\b").containsMatchIn(lower)) target.hours.add(22)

        val addedNamedHours = target.hours.toSet() - hadHoursBefore

        // Fréquence (seulement si on n'a pas trouvé d'horaires nommés dans cette instruction)
        if (addedNamedHours.isEmpty()) {
            extractFrequency(lower, target)
        }

        // Durée
        if (!target.hasExplicitDuration) {
            val durMatch = durationRegex.find(lower) ?: durationStandaloneRegex.find(lower)
            if (durMatch != null) {
                val value = durMatch.groupValues.filter { it.all { c -> c.isDigit() } && it.isNotEmpty() }
                    .firstOrNull()?.toIntOrNull()
                val unit = durMatch.groupValues.lastOrNull { it.matches(Regex("jour.*|semaine.*|mois|j")) } ?: ""
                if (value != null) {
                    target.durationDays = parseDurationValue(value, unit)
                    target.hasExplicitDuration = true
                }
            }
        }

        // Prise libre
        if (lower.contains("si besoin") || lower.contains("douleur") ||
            lower.contains("fièvre") || lower.contains("fievre") || lower.contains("ievre") ||
            lower.contains("en cas de") || lower.contains("en cas d") ||
            lower.contains("si nécessaire") || lower.contains("si necessaire")) {
            target.isPriseLibre = true
        }

        // Intervalle
        extractInterval(lower, target)
    }

    private fun extractFrequency(lower: String, target: MedBuilder) {
        if (target.hours.isNotEmpty()) return

        val freqMatch = frequencyRegex.find(lower)
        if (freqMatch != null) {
            var maxTimes = freqMatch.groupValues
                .filter { it.all { c -> c.isDigit() } && it.isNotEmpty() }
                .mapNotNull { it.toIntOrNull() }
                .maxOrNull()

            if (maxTimes == null) {
                maxTimes = freqMatch.groupValues
                    .mapNotNull { wordToNumber[it.lowercase()] }
                    .maxOrNull()
            }

            if (maxTimes != null) {
                applyFrequencyHours(maxTimes, target)
                return
            }
        }

        // Patterns simples + OCR-tolerant ("1 4 fois" = "1 à 4 fois")
        val simpleFoisMatch = Regex("(\\d)\\s*(?:à|a|)\\s*(\\d)\\s*fois").find(lower)
        if (simpleFoisMatch != null) {
            val max = simpleFoisMatch.groupValues.mapNotNull { it.toIntOrNull() }.maxOrNull()
            if (max != null) {
                applyFrequencyHours(max, target)
                return
            }
        }

        // Fallback textuel
        when {
            lower.contains("4 fois") || lower.contains("quatre fois") ->
                applyFrequencyHours(4, target)
            lower.contains("3 fois") || lower.contains("trois fois") ->
                applyFrequencyHours(3, target)
            lower.contains("2 fois") || lower.contains("deux fois") ->
                applyFrequencyHours(2, target)
            lower.contains("1 fois") || lower.contains("une fois") ->
                applyFrequencyHours(1, target)
        }
    }

    private fun applyFrequencyHours(times: Int, target: MedBuilder) {
        if (target.hours.isNotEmpty()) return
        when (times) {
            1 -> target.hours.add(8)
            2 -> target.hours.addAll(listOf(8, 20))
            3 -> target.hours.addAll(listOf(8, 12, 20))
            else -> target.hours.addAll(listOf(8, 12, 16, 20))
        }
    }

    private fun extractInterval(lower: String, target: MedBuilder) {
        val intervalMatch = intervalRegex.find(lower) ?: intervalAltRegex.find(lower)
        if (intervalMatch != null) {
            val hours = intervalMatch.groupValues
                .firstOrNull { it.all { c -> c.isDigit() } && it.isNotEmpty() }
                ?.toIntOrNull()
            if (hours != null && hours in 1..24) {
                target.intervalHours = hours
            }
        }
    }

    private fun parseDurationValue(value: Int, unit: String): Int {
        return when {
            unit.startsWith("semaine") -> value * 7
            unit == "mois" -> value * 30
            else -> value
        }
    }
}
