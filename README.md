# OrdoRead - CLAUDE.md

## Projet

Application Android native pour **numériser des ordonnances médicales via OCR** et créer automatiquement des rappels de médicaments dans le calendrier du téléphone. Public cible : personnes âgées ou en manque de capacités, pour gérer leurs ordonnances de manière autonome.

**Package** : `com.identifiant.ordoread`
**Version** : 1.0 (versionCode 1)

## Stack technique

- **Langage** : Kotlin 1.9.22, JVM target Java 17
- **UI** : Jetpack Compose + Material3 (BOM 2024.02.00)
- **Architecture** : Clean Architecture (presentation / domain / data) + MVVM
- **DI** : Hilt 2.50 (via KSP)
- **OCR** : Google ML Kit Text Recognition 19.0.0
- **IA locale** : llama.cpp (C++ via JNI) + Qwen3-0.6B (Q4_K_M, ~490 Mo)
- **Caméra** : CameraX 1.3.1
- **Async** : Kotlin Coroutines 1.7.3
- **Native** : NDK 26.1.10909125, CMake 3.22.1, cible arm64-v8a uniquement
- **DB** : Room 2.6.1 déclaré mais **non implémenté** (aucune entité/DAO)
- **SDK** : minSdk 26, targetSdk 34, compileSdk 34
- **Build** : Gradle (Android Gradle Plugin)

## Structure du projet

```
app/src/main/java/com/identifiant/ordoread/
├── MainActivity.kt              # Single Activity, UI Compose, insertion calendrier
├── OrdoReadApp.kt               # Application class (Hilt)
├── data/
│   ├── device/
│   │   ├── MLKitTextRecognitionService.kt   # Implémentation OCR ML Kit
│   │   └── ImagePreprocessor.kt             # Preprocessing image (grayscale + contraste)
│   ├── parser/
│   │   └── LocalPrescriptionAnalyzer.kt     # Parser local regex (fallback, 2 passes)
│   └── ai/
│       ├── LlamaCpp.kt                      # Wrapper JNI llama.cpp (load/generate/free)
│       ├── LlamaCppPrescriptionAnalyzer.kt  # Correction des résultats regex via Qwen3
│       ├── SmartPrescriptionAnalyzer.kt     # Orchestrateur : regex + correction LLM
│       └── ModelDownloadManager.kt          # Téléchargement Qwen3 depuis HuggingFace
├── domain/
│   ├── ai/
│   │   └── PrescriptionAnalyzer.kt          # Interface + data class Medication
│   └── device/
│       └── TextRecognitionService.kt        # Interface OCR
├── presentation/
│   ├── MainViewModel.kt                     # ViewModel (HiltViewModel), StateFlow
│   └── ocr/
│       └── CameraPreview.kt                # Composable caméra CameraX
├── di/
│   └── DeviceModule.kt                      # Module Hilt (injecte SmartPrescriptionAnalyzer)
├── ui/theme/                                # Color.kt, Theme.kt, Type.kt
└── utils/
    └── StorageUtils.kt                      # Sauvegarde images JPEG

app/src/main/cpp/
├── CMakeLists.txt                           # Build llama.cpp comme sous-projet
└── llama_jni.cpp                            # Bridge JNI : loadModel, generate, freeModel, isModelLoaded

llama.cpp/                                   # Sous-module llama.cpp (C++)
```

## Pipeline d'analyse des ordonnances

L'application est **100% offline** — aucune donnée ne quitte le téléphone.

1. **Preprocessing image** (`ImagePreprocessor`) : photo brute → image optimisée pour OCR
   - Redimensionnement si >2000px
   - Conversion en niveaux de gris (supprime le bruit de couleur)
   - Léger boost de contraste (1.2x, brightness -10)
   - **Fallback** : si l'OCR ne détecte rien après preprocessing → réessai sur l'image brute
   - **Binarisation NON utilisée** : testée et abandonnée — trop agressive, détruit les détails sur les photos de téléphone (ombres, éclairage inégal). ML Kit gère mieux les images grayscale avec bon contraste.
2. **OCR** (ML Kit) : image preprocessée → texte brut
3. **Corrections OCR** (`fixOcrErrors` dans LocalPrescriptionAnalyzer) : normalisation agressive du texte avant parsing
4. **Analyse hybride** (`SmartPrescriptionAnalyzer`) :
   - **Étape 1** : Parser regex (`LocalPrescriptionAnalyzer`) — extraction rapide des médicaments (~0ms). Parsing en 2 passes :
     - Passe 1 : identifier les lignes médicaments (filtre positif pharmaceutique + dictionnaire 80+ noms)
     - Passe 1 bis : identifier les lignes d'instruction (posologie, horaires, fréquence, durée, prise libre)
     - Passe 2 : rattacher les instructions au bon médicament par matching forme galénique + proximité
   - **Étape 2** : Correction LLM (`LlamaCppPrescriptionAnalyzer`) — si le modèle Qwen3 est disponible, il corrige/valide le résultat regex
   - **Fusion intelligente** : noms LLM (si corrects) ou regex, hours OR (LLM prioritaire), durée validée (1-90j), prise libre OR, interval max
   - **Fallback** : si le LLM n'est pas dispo ou échoue → résultat regex seul

## LLM local (Qwen3-0.6B via llama.cpp)

### Architecture native

- **llama.cpp** compilé en tant que bibliothèque statique via CMake
- **JNI bridge** (`llama_jni.cpp`) expose 4 fonctions natives : `loadModel`, `generate`, `freeModel`, `isModelLoaded`
- **Wrapper Kotlin** (`LlamaCpp.kt`) gère le cycle de vie du modèle avec coroutines IO
- Bibliothèque partagée : `libordoread-llama.so` (arm64 uniquement)

### Modèle

- **Qwen3-0.6B** quantifié Q4_K_M (~490 Mo)
- Format GGUF, stocké dans `context.filesDir/models/qwen3-0.6b-q4_k_m.gguf`
- Téléchargement depuis HuggingFace (public, pas de token nécessaire)
- URL : `https://huggingface.co/unsloth/Qwen3-0.6B-GGUF/resolve/main/Qwen3-0.6B-Q4_K_M.gguf`

### Approche hybride (regex + LLM)

Le LLM ne fait pas l'analyse complète — il **corrige** le résultat du parser regex :
- Prompt ChatML (`<|im_start|>`) avec 2 few-shot examples + texte OCR pertinent + résultat regex en format compact
- **Format compact** (pas JSON) : `NomMédicament 500mg|h=[8,20]|d=7` ou `Doliprane 1g|h=[]|d=10,libre,interval=6h`
- Le prompt pré-remplit le début de la première ligne pour **forcer le modèle à continuer dans le format** au lieu d'ouvrir un `<think>`
- Limite : `regexResult.size * 60` tokens (adaptative)
- Si le nombre de lignes LLM ≠ regex → on garde le regex (sécurité)
- Double parser : format compact en priorité, fallback JSON

### Problème connu : mode "thinking" de Qwen3

Qwen3-0.6B a un mode `<think>` intégré qui s'active spontanément. **Le `/no_think` ne fonctionne pas** via llama.cpp GGUF. Solutions implémentées :
1. Pré-remplir le début de la réponse avec `NomMédicament|` pour forcer le format
2. Strip des blocs `<think>...</think>` dans la réponse
3. Si `<think>` non fermé (modèle n'a pas fini de réfléchir) → tout est supprimé, fallback regex
4. Strip des tokens ChatML parasites (`<|im_start|>`, `<|im_end|>`) dans la réponse
5. `.distinct()` sur les lignes parsées pour éliminer les doublons générés par le LLM

**Si le LLM continue de "think" au lieu de répondre** : augmenter `maxTokens` pour qu'il ait le temps de finir `</think>` puis répondre, OU chercher un moyen d'injecter un stop token `<think>` dans la config llama.cpp (non implémenté).

### Modèles alternatifs testés (à la racine du projet)

- `smollm2-135m-instruct-q8.gguf` — trop petit, résultats insuffisants
- `smollm2-360m-q4.gguf` — idem
- `qwen3-0.6b-q4_k_m.gguf` — modèle retenu (bon compromis taille/qualité)

## Parser regex — détails techniques

### Corrections OCR (`fixOcrErrors`)

Le texte OCR est très dégradé sur les ordonnances. Le parser applique des corrections agressives avant le parsing :

**Chiffres** :
- `I`/`l` devant chiffres+unité → `1` (ex: "I80ml" → "180ml")
- `O` entre chiffres → `0` (ex: "1O0mg" → "100mg")
- `s` devant chiffres+unité → `5` (ex: "s00 mg" → "500 mg")
- `T` devant "jours" → `7` (ex: "Tjours" → "7 jours")

**Unités** :
- `ng` précédé de chiffres → `mg` (OCR confond n/m)
- `comprim` tronqué → `comprimé`
- `mg` collé à un chiffre suivant → `mg/` (ex: "mg30" → "mg/30", dosages combinés)

**Mots médicaux** :
- "srop"/"sop"/"siop" → "sirop"
- "pélule"/"pelule" → "gélule"
- "comprlmé"/"comprîmé"/"comprimd"/"camprmé"/"com primé"/"comprimné" → "comprimé"
- "Teuale a soupe" → "cuillère à soupe"
- "Neo-eodion" → "Neo-Codion"
- "PARACETAMIOL"/"paracetamiol"/"Paracátamal" → "Paracétamol"
- "codäine"/"cadálne" → "codéine"

**Fréquence/durée** :
- "lpar gours"/"Ipar jour" → "1 par jour"
- "gours"/"gour" → "jour"
- "pendane"/"pendam"/"pendkant" → "pendant"
- "heuses"/"beures"/"heres" → "heures"
- "mafin"/"mahn" → "matin", "solr"/"sojr"/"sor" → "soir", "mid" → "midi"

**Prise libre** :
- "douleus"/"douieur" → "douleur"
- "fee"/"fée" (en contexte "ou fee") → "fièvre"
- "ai douleur"/"ai fièvre" → "si douleur"/"si fièvre" (OCR confond ai/si)

**Noms de médicaments courants** (corrections OCR spécifiques) :
- Doliprane (Dolibrane, Doiiprane, Dolipane...)
- Augmentin, Amoxicilline, Levothyrox, Ventoline
- Spasfon, Tramadol, Lexomil, Solupred, Kardegic, Gaviscon, Seroplex

### Détection des médicaments (Passe 1)

Un ligne est identifiée comme médicament si :
- Elle contient un **indicateur pharmaceutique** (mg, ml, comprimé, gélule, sirop, etc.) OU
- Elle contient un **nom de médicament connu** (dictionnaire de ~150 noms organisé par catégorie : antibiotiques, diabète, gastro, cardio, psycho/neuro, douleur, corticoïdes, respiratoire, urologie, ophtalmo, dermato, vitamines, etc.)
- ET **ne commence PAS** par une instruction de posologie ("1 comprimé...") ou un horaire ("matin...")

**Cas spécial** : ligne mixte (médicament + posologie sur la même ligne) → détectée et traitée comme les deux. Exige un **nom de médicament connu** (dictionnaire) pour être classée MED+INSTR — sinon c'est une instruction pure (évite "1 (un) comprimé le matin..." classé comme médicament).

### Détection des instructions (Passe 1 bis)

Une ligne est captée comme instruction si elle contient :
- Un pattern de posologie : "1 comprimé", "2 gélules", "1 cuillère à soupe"
- Un pattern de fréquence/horaire : "matin", "soir", "midi", "3 fois par jour", "toutes les 6 heures"
- Un pattern de durée : "pendant 10 jours"
- Un mot-clé de prise libre : "douleur", "fièvre", "si besoin"
- Un mot-clé d'intervalle : "toutes les X heures"

### Rattachement des instructions (Passe 2)

Stratégie ordonnée :
1. **Match par forme galénique** (le plus fiable) : "cuillère" → médicament de type "sirop/solution", "gélule" → médicament de type "gélule/capsule"
2. **Proximité** : le médicament le plus proche au-dessus (convention ordonnance : instructions sous le médicament)
3. **Seuil de distance** : si le médicament au-dessus est à >5 lignes, vérifier s'il y en a un plus proche en-dessous

### Extraction inline

Le parser extrait directement depuis la ligne du médicament (sans attendre la passe 2) :
- **Horaires nommés** : "matin", "midi", "soir", "coucher" → hours
- **Fréquence** : "3 fois par jour", "1 à 4 fois", "2 par jour" → hours calculés
- **Durée** : "pendant 10 jours", "2 semaines" → durationDays
- **Prise libre** : "si besoin", "si douleur", "en cas de" → isPriseLibre
- **Intervalle** : "toutes les 6 heures" → intervalHours

### Durée avec texte en parenthèses

Le `durationRegex` accepte un texte entre parenthèses entre le chiffre et l'unité :
- "pendant 5 (cinq) jours" → 5 jours ✓
- "pendant 5 (cing) jours" → 5 jours ✓ (OCR de "cinq")

### Posologie avec quantité en toutes lettres

Le `posologyLineRegex` accepte du texte entre parenthèses entre le chiffre et la forme galénique :
- "1 (un) comprimé le matin..." → classé comme instruction ✓
- "1(un) comprimé..." → idem ✓

### Nettoyage des noms de médicaments

- Suppression des artefacts "I"/"T"/"1"/"0"/"+" en début de nom (numérotation OCR)
- "géI" → "gélule" (OCR confond l/I en fin de mot)
- "I80ml" → "180ml"
- "siop" → "sirop"
- Ponctuation parasite retirée

## Fonctionnalités implémentées

1. **Prévisualisation photo** : Après capture ou import, l'image est affichée pour validation. L'utilisateur vérifie que tous les médicaments sont visibles avant de lancer l'analyse. Boutons "Analyser cette photo" / "Reprendre la photo".
2. **OCR** : Image validée → preprocessing light → ML Kit extrait le texte
3. **Analyse intelligente** : Parser regex + correction LLM local (100% offline)
4. **Téléchargement modèle** : Au premier lancement, propose de télécharger Qwen3 (~490 Mo). Peut être ignoré (mode regex seul). Barre de progression.
5. **Edition des médicaments** :
   - Nom / posologie modifiable
   - Durée en jours
   - Horaires via **FilterChips toggleables** (Matin 8h, Midi 12h, Après-midi 16h, Soir 20h, Coucher 22h) + horaires personnalisés via InputChips
   - Résumé affiché : "X prise(s)/jour : 8h, 12h, 20h"
6. **Gestion des prises libres (si besoin)** :
   - Switch "Prise si besoin" pour marquer un médicament en prise libre
   - Par défaut, les prises libres ne sont **pas ajoutées au calendrier**
   - Switch vert/rouge "Ajouter au calendrier" avec indication de l'intervalle détecté
   - Si activé : champ "Intervalle (heures)" pré-rempli depuis l'ordonnance
   - Génération automatique des horaires à partir de l'intervalle (ex: 6h → 8h, 14h, 20h)
7. **Choix du début de traitement** :
   - Dialog "Aujourd'hui" / "Demain" avant insertion calendrier
   - **Aujourd'hui** : crée les prises restantes du jour comme événements uniques, puis les jours complets en récurrent
   - **Demain** : planning complet dès le lendemain
   - Indication si il est tard (≥20h) que certaines prises sont passées
8. **Calendrier** : Insertion de rappels récurrents (RRULE DAILY) avec alarmes
9. **Historique** : Sauvegarde des images d'ordonnance en stockage interne, affichage avec miniatures, suppression

## Navigation

Single Activity avec Bottom Navigation Compose :
- **Scanner** : Caméra live + capture + résultats OCR
- **Importer** : Sélection d'image depuis fichiers
- **Historique** : Liste des ordonnances sauvegardées

Gestion d'état via enum `Screen { SCANNER, HISTORY }`.

## Modèle de données

```kotlin
data class Medication(
    val description: String,          // Nom/dosage du médicament
    val hours: List<Int>,             // Heures de prise (0-23)
    val durationDays: Int = 7,        // Durée en jours
    val isPriseLibre: Boolean = false, // Prise libre (si besoin)
    val intervalHours: Int = 0,       // Intervalle min entre prises (0 = non détecté)
    val addToCalendar: Boolean = true  // false par défaut pour prises libres
)
```

## State management

- `MainViewModel` étend `ViewModel` (injection Hilt standard)
- Expose un `StateFlow<UiState>` (sealed class : Idle, Preview, Processing, Success, Error)
- **Preview** : l'utilisateur valide la photo avant analyse (vérifie que tous les médicaments sont visibles)
- `previewImage(bitmap)` → affiche la prévisualisation, `confirmAndProcess(bitmap)` → lance l'analyse
- Les Composables observent via `collectAsState()`

## Permissions Android

- `CAMERA` (obligatoire)
- `READ_CALENDAR` / `WRITE_CALENDAR`
- `INTERNET` (uniquement pour le téléchargement initial du modèle)

## Confidentialité

L'application est **100% offline** après téléchargement du modèle :
- Aucune donnée médicale ne quitte le téléphone
- L'OCR (ML Kit) fonctionne en local
- L'analyse (llama.cpp + Qwen3) fonctionne en local
- Seul le téléchargement initial du modèle nécessite internet
- Les images d'ordonnance sont stockées uniquement en interne

## Device de test

- **Samsung Galaxy A34** (SM-A346B), 6 Go RAM
- ADB : `<ANDROID_SDK>/platform-tools/adb.exe`
- **Important sur Git Bash Windows** : utiliser `MSYS_NO_PATHCONV=1` devant les commandes `adb` pour éviter que Git Bash convertisse les chemins Android (`/data/local/tmp` → `C:/Program Files/Git/data/...`)
- Logs utiles : `adb logcat -s SmartParser OrdoParser LlamaCpp LlamaCppAnalyzer`

## Build & Run

```bash
./gradlew assembleDebug        # Build debug APK (inclut compilation native llama.cpp)
./gradlew installDebug         # Install sur device/emulateur connecté

# Ou via ADB directement (nécessaire sur Git Bash Windows) :
MSYS_NO_PATHCONV=1 adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Note : le premier build est long car il compile llama.cpp en C++ pour arm64.

## Tests

Quasi inexistants - uniquement les placeholders auto-générés :
- `test/ExampleUnitTest.kt` (JUnit basique)
- `androidTest/ExampleInstrumentedTest.kt` (vérifie le contexte)

## Points d'attention

- **Room non utilisé** : Les dépendances sont déclarées mais aucune base de données n'est créée. Les données ne persistent que sous forme d'images JPEG.
- **Pas de tests** : Aucun test unitaire sur la logique métier (parsing, ViewModel).
- **Images non chiffrées** : Stockées en clair dans le stockage interne.
- **Langue** : L'app est conçue pour les ordonnances **françaises** uniquement.
- **Logs de debug** : Le parser local et llama.cpp contiennent des `Log.d`/`Log.w` — à retirer en production.
- **Modèles GGUF à la racine** : Fichiers de test (~490 Mo+) présents à la racine du projet — ne pas versionner.
- **Doublon** : `MLKitTextRecognitionService.kt` existe dans `data/device/` (correct) ET `domain/device/` (à nettoyer).
- **arm64 uniquement** : L'APK ne supporte que `arm64-v8a` — pas de support x86 (émulateur) ni armv7.
- **Qwen3 <think>** : Le modèle entre en mode réflexion interne malgré les instructions contraires. Le pré-remplissage du prompt aide mais n'est pas garanti à 100%.

## Conventions de code

- Kotlin avec Compose idiomatique
- Injection par constructeur via `@Inject` / `@HiltViewModel`
- Interfaces dans `domain/`, implémentations dans `data/`
- Composables dans `presentation/` et `MainActivity.kt`
- Pas de commentaires KDoc, code concis
- Fonctions utilitaires (`generateHoursFromInterval`, `insertMedicationsIntoCalendar`) dans MainActivity

## Process d'amélioration du parser et du prompt

### Méthodologie de test

1. **Scanner une ordonnance** sur le Samsung A34
2. **Lire les logs** : `MSYS_NO_PATHCONV=1 adb logcat -d | grep -iE "(SmartParser|OrdoParser|LlamaCpp)"`
3. **Analyser** :
   - Quelles lignes OCR sont correctement identifiées comme médicaments ?
   - Quelles instructions sont correctement rattachées ?
   - Le LLM a-t-il répondu dans le format attendu ?
   - Quelles erreurs OCR ne sont pas corrigées ?
4. **Corriger** le parser/prompt dans le code
5. **Build/install** : `./gradlew assembleDebug && MSYS_NO_PATHCONV=1 adb install -r app/build/outputs/apk/debug/app-debug.apk`
6. **Clear logs** : `MSYS_NO_PATHCONV=1 adb logcat -c`
7. **Re-scanner** et vérifier

### Axes d'amélioration du parser regex

Quand on rencontre une nouvelle erreur OCR non gérée :
1. **Identifier le pattern** dans les logs (ligne brute OCR vs texte attendu)
2. **Ajouter une correction** dans `fixOcrErrors()` — utiliser un Regex permissif
3. **Vérifier que la correction ne casse pas** d'autres cas (pas de regex trop large)
4. **Si c'est un nouveau médicament non reconnu** : l'ajouter dans `knownDrugNames`
5. **Si c'est un nouveau pattern d'instruction** : l'ajouter dans les conditions de `instructionLines`

### Axes d'amélioration du prompt LLM

Le problème principal est que Qwen3-0.6B est **trop petit** pour des tâches complexes. Pour maximiser la qualité :
1. **Réduire la tâche au minimum** : ne demander que des corrections simples (noms, hours, durées)
2. **Format compact** : pas de JSON (trop de tokens gaspillés sur les accolades/guillemets)
3. **Few-shot examples** : 2-3 exemples concrets dans le prompt (critique pour les petits modèles)
4. **Pré-remplir la réponse** : forcer le début pour éviter que le modèle divague ou entre en `<think>`
5. **Tokens adaptatifs** : calculer le max nécessaire selon le nombre de médicaments
6. **Fallback gracieux** : si le LLM échoue, le résultat regex est déjà bon dans 80% des cas

### Observations terrain (Samsung A34)

- **Temps de chargement modèle** : ~1-2s
- **Temps d'inférence** : ~20-30s pour 1 médicament, ~50-90s pour 2+ médicaments (trop lent si mode <think> actif)
- **Qualité OCR** : très variable selon l'éclairage et la netteté de la photo. Le même mot peut être lu différemment à chaque scan.
- **L'OCR ML Kit est le goulot** : la qualité du texte extrait conditionne tout. Une photo floue = OCR inutilisable.
- **Le parser regex est le composant le plus fiable** : il fonctionne en <1ms, détecte correctement 80%+ des médicaments/instructions, et ne dépend pas d'un modèle IA imprévisible.

## Historique des décisions techniques

1. **Parser regex → approche hybride** : Le parser regex seul n'associe pas toujours correctement les instructions aux bons médicaments. Le LLM local corrige ces erreurs.
2. **Groq API → llama.cpp (100% offline)** : Initialement, l'app utilisait Groq (Llama 3.1 8B) en cloud avec anonymisation. Remplacé par llama.cpp + Qwen3-0.6B pour un fonctionnement 100% offline — plus besoin d'anonymiser, aucune donnée ne quitte le téléphone.
3. **MediaPipe Gemma 2B → llama.cpp Qwen3** : MediaPipe était trop lent (GPU Mali non supporté, CPU inutilisable). llama.cpp avec un modèle plus petit (0.6B vs 2B) et mieux quantifié est plus viable.
4. **Approche hybride regex+LLM** : Plutôt que de faire tout analyser par le LLM (lent, risque d'hallucination), le regex fait le gros du travail et le LLM ne fait que corriger — tâche plus simple = réponse plus rapide et fiable.
5. **Qwen3-0.6B retenu** : Après tests de SmolLM2-135M et SmolLM2-360M (trop petits), Qwen3-0.6B offre le meilleur compromis taille/qualité pour la correction d'ordonnances françaises.
6. **Format compact pour le LLM** : Le JSON consomme trop de tokens sur un 0.6B. Le format pipe-separated (`nom|h=[...]|d=X`) est 3x plus compact et plus facile à parser pour le modèle.
7. **Pré-remplissage du prompt** : Qwen3 entre systématiquement en `<think>` mode. Pré-remplir la réponse avec le début du premier médicament force le modèle à continuer dans le format attendu.
8. **Parser regex comme source de vérité** : Le LLM ne peut que corriger/améliorer le regex, jamais ajouter ou supprimer de médicaments. Si le nombre de résultats diffère, le regex gagne.
9. **Preprocessing light (pas de binarisation)** : La binarisation adaptative (Otsu par blocs) a été testée mais détruit trop d'information sur les photos de téléphone (éclairage inégal, ombres). Le mode light (grayscale + contraste léger 1.2x) améliore l'OCR sans risque. Fallback sur image brute si l'OCR échoue après preprocessing.
10. **Prévisualisation avant analyse** : L'utilisateur peut vérifier que la photo contient tous les médicaments avant de lancer l'analyse. Réduit les scans inutiles et améliore l'expérience pour le public cible (personnes âgées).
