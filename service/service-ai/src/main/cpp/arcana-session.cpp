#include "arcana-session.h"

#include <cmath>

namespace arcana {

Session *Session::load(const char *path, int n_ctx, int threads, int batch_threads) {
    llama_model_params mp = llama_model_default_params();
    mp.n_gpu_layers = 0;
    llama_model *model = llama_model_load_from_file(path, mp);
    if (!model) return nullptr;

    llama_context_params cp = llama_context_default_params();
    cp.n_ctx = static_cast<uint32_t>(n_ctx);
    cp.n_batch = 256;
    cp.n_threads = threads;
    cp.n_threads_batch = batch_threads;
    llama_context *ctx = llama_init_from_model(model, cp);
    if (!ctx) {
        llama_model_free(model);
        return nullptr;
    }

    auto *s = new Session();
    s->model = model;
    s->ctx = ctx;
    s->vocab = llama_model_get_vocab(model);
    // The model carries its own chat format inside the GGUF.
    if (const char *tmpl = llama_model_chat_template(model, nullptr)) s->chat_template = tmpl;
    if (s->chat_template.find("<think>") != std::string::npos) s->opening = "<think>\n\n</think>\n\n";
    return s;
}

Session::~Session() {
    if (rules) llama_sampler_free(rules);
    if (chain) llama_sampler_free(chain);
    llama_free(ctx);
    llama_model_free(model);
}

void Session::begin(const std::string &system, float temperature, int top_k, float top_p, float repeat_penalty, uint32_t seed) {
    llama_memory_clear(llama_get_memory(ctx), true);
    messages.clear();
    given = 0;
    pending.clear();
    fed = 0;
    used = 0;
    fresh = true;
    if (!system.empty()) messages.emplace_back("system", system);

    if (rules) {
        llama_sampler_free(rules);
        rules = nullptr;
    }
    if (chain) llama_sampler_free(chain);
    chain = llama_sampler_chain_init(llama_sampler_chain_default_params());
    llama_sampler_chain_add(chain, llama_sampler_init_penalties(64, repeat_penalty, 0.0f, 0.0f));
    llama_sampler_chain_add(chain, llama_sampler_init_top_k(top_k));
    llama_sampler_chain_add(chain, llama_sampler_init_top_p(top_p, 1));
    llama_sampler_chain_add(chain, llama_sampler_init_temp(temperature));
    llama_sampler_chain_add(chain, llama_sampler_init_dist(seed));
}

std::string Session::format(bool open_reply) const {
    std::vector<llama_chat_message> chat;
    chat.reserve(messages.size());
    size_t length = 256;
    for (const auto &m : messages) {
        chat.push_back({m.first.c_str(), m.second.c_str()});
        length += m.first.size() + m.second.size() + 64;
    }
    std::vector<char> buffer(length);
    const char *tmpl = chat_template.empty() ? "chatml" : chat_template.c_str();
    int n = llama_chat_apply_template(tmpl, chat.data(), chat.size(), open_reply, buffer.data(), static_cast<int32_t>(buffer.size()));
    if (n > static_cast<int>(buffer.size())) {
        buffer.resize(static_cast<size_t>(n) + 1);
        n = llama_chat_apply_template(tmpl, chat.data(), chat.size(), open_reply, buffer.data(), static_cast<int32_t>(buffer.size()));
    }
    if (n < 0) {
        // A template llama.cpp does not know. ChatML is what most small chat models were trained on.
        std::string out;
        for (const auto &m : messages) out += "<|im_start|>" + m.first + "\n" + m.second + "<|im_end|>\n";
        if (open_reply) out += "<|im_start|>assistant\n";
        return out;
    }
    return std::string(buffer.data(), static_cast<size_t>(n));
}

int Session::user(const std::string &text, int reserve) {
    messages.emplace_back("user", text);
    std::string all = format(true);
    std::string delta = (given <= all.size() ? all.substr(given) : all) + opening;

    // Nothing is kept unless the turn is accepted, so a refused one leaves the session as it was.
    std::vector<llama_token> tokens;
    int n = -llama_tokenize(vocab, delta.c_str(), static_cast<int32_t>(delta.size()), nullptr, 0, fresh, true);
    if (n > 0) {
        tokens.resize(static_cast<size_t>(n));
        if (llama_tokenize(vocab, delta.c_str(), static_cast<int32_t>(delta.size()), tokens.data(), n, fresh, true) < 0) n = 0;
    }
    const int waiting = static_cast<int>(pending.size() - fed);
    const int refused = n <= 0 ? UNREADABLE : used + waiting + n + reserve > static_cast<int>(llama_n_ctx(ctx)) ? CONTEXT_FULL : 0;
    if (refused) {
        messages.pop_back();
        return refused;
    }
    given = all.size();
    fresh = false;
    pending.insert(pending.end(), tokens.begin(), tokens.end());
    return waiting + n;
}

int Session::feed(int max_tokens) {
    const int waiting = static_cast<int>(pending.size() - fed);
    const int n = waiting < max_tokens ? waiting : max_tokens;
    if (n > 0) {
        if (llama_decode(ctx, llama_batch_get_one(pending.data() + fed, n)) != 0) return FAILED;
        // The prompt is deliberately kept out of the repetition memory: a reply should be free
        // to name the card it was just asked about.
        fed += static_cast<size_t>(n);
        used += n;
    }
    if (fed == pending.size()) {
        pending.clear();
        fed = 0;
    }
    return static_cast<int>(pending.size() - fed);
}

bool Session::grammar(const std::string &gbnf) {
    if (rules) {
        llama_sampler_free(rules);
        rules = nullptr;
    }
    if (gbnf.empty()) return true;
    rules = llama_sampler_init_grammar(vocab, gbnf.c_str(), "root");
    return rules != nullptr;
}

void Session::fill_candidates() {
    const float *logits = llama_get_logits_ith(ctx, -1);
    const int n = llama_vocab_n_tokens(vocab);
    candidates.resize(static_cast<size_t>(n));
    for (int i = 0; i < n; i++) candidates[static_cast<size_t>(i)] = llama_token_data{i, logits[i], 0.0f};
}

// Picks the next token. With a grammar set, the cheap path is tried first: sample freely, then
// ask the grammar about that one token. Only a refusal pays for checking the whole vocabulary.
llama_token Session::sample() {
    fill_candidates();
    llama_token_data_array all = {candidates.data(), candidates.size(), -1, false};
    llama_sampler_apply(chain, &all);
    llama_token id = all.data[all.selected < 0 ? 0 : all.selected].id;
    if (rules) {
        llama_token_data one = {id, 1.0f, 0.0f};
        llama_token_data_array single = {&one, 1, -1, false};
        llama_sampler_apply(rules, &single);
        if (std::isinf(one.logit) && one.logit < 0) {
            fill_candidates();
            all = {candidates.data(), candidates.size(), -1, false};
            llama_sampler_apply(rules, &all);
            llama_sampler_apply(chain, &all);
            id = all.data[all.selected < 0 ? 0 : all.selected].id;
        }
    }
    return id;
}

bool Session::next(std::string &piece) {
    piece.clear();
    if (fed != pending.size() || used + 1 >= static_cast<int>(llama_n_ctx(ctx))) return false;
    llama_token id = sample();
    if (llama_vocab_is_eog(vocab, id)) return false;
    if (rules) llama_sampler_accept(rules, id);
    llama_sampler_accept(chain, id);
    char text[256];
    int n = llama_token_to_piece(vocab, id, text, sizeof(text), 0, false);
    if (n > 0) piece.assign(text, static_cast<size_t>(n));
    if (llama_decode(ctx, llama_batch_get_one(&id, 1)) != 0) return false;
    used += 1;
    return true;
}

void Session::reply(const std::string &text) {
    // The model has been given everything up to the open reply, and has written the reply itself.
    given += text.size();
    messages.emplace_back("assistant", text);
}

int Session::context_left() const {
    return static_cast<int>(llama_n_ctx(ctx)) - used - static_cast<int>(pending.size() - fed);
}

}  // namespace arcana
