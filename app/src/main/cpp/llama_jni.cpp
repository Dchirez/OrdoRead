#include <jni.h>
#include <string>
#include <vector>
#include <algorithm>
#include <android/log.h>
#include "llama.h"

#define TAG "LlamaJNI"
#define LOG_I(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOG_E(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

static llama_model * model = nullptr;

// Helper: ajouter un token au batch (remplace common_batch_add)
static void batch_add(llama_batch & batch, llama_token id, llama_pos pos,
                      const std::vector<llama_seq_id> & seq_ids, bool logits) {
    batch.token[batch.n_tokens] = id;
    batch.pos[batch.n_tokens] = pos;
    batch.n_seq_id[batch.n_tokens] = seq_ids.size();
    for (size_t i = 0; i < seq_ids.size(); i++) {
        batch.seq_id[batch.n_tokens][i] = seq_ids[i];
    }
    batch.logits[batch.n_tokens] = logits;
    batch.n_tokens++;
}

// Helper: vider le batch (remplace common_batch_clear)
static void batch_clear(llama_batch & batch) {
    batch.n_tokens = 0;
}

extern "C" {

JNIEXPORT jboolean JNICALL
Java_com_identifiant_ordoread_data_ai_LlamaCpp_loadModel(
        JNIEnv *env, jobject /* this */, jstring modelPath) {

    if (model != nullptr) {
        LOG_I("Modèle déjà chargé, réutilisation");
        return JNI_TRUE;
    }

    const char *path = env->GetStringUTFChars(modelPath, nullptr);
    LOG_I("Chargement du modèle: %s", path);

    llama_backend_init();

    llama_model_params model_params = llama_model_default_params();
    model_params.n_gpu_layers = 0; // CPU only pour compatibilité maximale

    model = llama_model_load_from_file(path, model_params);
    env->ReleaseStringUTFChars(modelPath, path);

    if (model == nullptr) {
        LOG_E("Échec du chargement du modèle");
        return JNI_FALSE;
    }

    LOG_I("Modèle chargé avec succès");
    return JNI_TRUE;
}

JNIEXPORT jstring JNICALL
Java_com_identifiant_ordoread_data_ai_LlamaCpp_generate(
        JNIEnv *env, jobject /* this */, jstring prompt, jint maxTokens) {

    if (model == nullptr) {
        LOG_E("Modèle non chargé");
        return env->NewStringUTF("");
    }

    const char *promptStr = env->GetStringUTFChars(prompt, nullptr);
    std::string promptCopy(promptStr);
    env->ReleaseStringUTFChars(prompt, promptStr);

    // Créer le contexte
    llama_context_params ctx_params = llama_context_default_params();
    ctx_params.n_ctx = 2048;
    ctx_params.n_batch = 512;
    ctx_params.n_threads = 4;

    llama_context * ctx = llama_init_from_model(model, ctx_params);
    if (ctx == nullptr) {
        LOG_E("Échec de la création du contexte");
        return env->NewStringUTF("");
    }

    // Tokeniser le prompt
    const llama_vocab * vocab = llama_model_get_vocab(model);
    int prompt_len = promptCopy.size();

    // Premier appel pour connaître la taille nécessaire
    int n_tokens = llama_tokenize(vocab, promptCopy.c_str(), prompt_len,
                                   nullptr, 0, true, true);
    if (n_tokens < 0) {
        n_tokens = -n_tokens;
    }

    std::vector<llama_token> tokens(n_tokens);
    n_tokens = llama_tokenize(vocab, promptCopy.c_str(), prompt_len,
                               tokens.data(), tokens.size(), true, true);

    if (n_tokens < 0) {
        LOG_E("Échec de la tokenisation");
        llama_free(ctx);
        return env->NewStringUTF("");
    }
    tokens.resize(n_tokens);

    LOG_I("Prompt tokenisé: %d tokens", n_tokens);

    // Évaluer le prompt par chunks de 512
    const int batch_size = 512;
    llama_batch batch = llama_batch_init(batch_size, 0, 1);

    for (int i = 0; i < n_tokens; i += batch_size) {
        batch_clear(batch);
        int end = std::min(i + batch_size, n_tokens);
        for (int j = i; j < end; j++) {
            batch_add(batch, tokens[j], j, {0}, j == n_tokens - 1);
        }

        if (llama_decode(ctx, batch) != 0) {
            LOG_E("Échec du décodage à la position %d", i);
            llama_batch_free(batch);
            llama_free(ctx);
            return env->NewStringUTF("");
        }
    }

    // Générer les tokens
    std::string result;
    int n_cur = n_tokens;
    int n_gen = 0;

    // Sampler
    llama_sampler * sampler = llama_sampler_chain_init(llama_sampler_chain_default_params());
    llama_sampler_chain_add(sampler, llama_sampler_init_temp(0.1f));
    llama_sampler_chain_add(sampler, llama_sampler_init_top_k(20));
    llama_sampler_chain_add(sampler, llama_sampler_init_top_p(0.9f, 1));
    llama_sampler_chain_add(sampler, llama_sampler_init_dist(42));

    while (n_gen < (int)maxTokens) {
        llama_token new_token = llama_sampler_sample(sampler, ctx, batch.n_tokens - 1);

        // Vérifier fin de génération
        if (llama_vocab_is_eog(vocab, new_token)) {
            break;
        }

        // Convertir token en texte
        char buf[256];
        int n = llama_token_to_piece(vocab, new_token, buf, sizeof(buf), 0, true);
        if (n > 0) {
            result.append(buf, n);
        }

        // Arrêter si on détecte la fin du JSON
        if (result.find("]\n") != std::string::npos ||
            (result.length() > 10 && result.back() == ']')) {
            break;
        }

        // Préparer le batch suivant
        batch_clear(batch);
        batch_add(batch, new_token, n_cur, {0}, true);

        if (llama_decode(ctx, batch) != 0) {
            LOG_E("Échec du décodage à la position %d", n_cur);
            break;
        }

        n_cur++;
        n_gen++;
    }

    LOG_I("Génération terminée: %d tokens générés", n_gen);

    llama_sampler_free(sampler);
    llama_batch_free(batch);
    llama_free(ctx);

    return env->NewStringUTF(result.c_str());
}

JNIEXPORT void JNICALL
Java_com_identifiant_ordoread_data_ai_LlamaCpp_freeModel(
        JNIEnv *env, jobject /* this */) {
    if (model != nullptr) {
        llama_model_free(model);
        model = nullptr;
        llama_backend_free();
        LOG_I("Modèle libéré");
    }
}

JNIEXPORT jboolean JNICALL
Java_com_identifiant_ordoread_data_ai_LlamaCpp_isModelLoaded(
        JNIEnv *env, jobject /* this */) {
    return model != nullptr ? JNI_TRUE : JNI_FALSE;
}

} // extern "C"
