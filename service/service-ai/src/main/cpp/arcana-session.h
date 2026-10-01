// One conversation with a GGUF chat model through llama.cpp, a turn at a time. This part knows
// nothing about Android, so the same code runs in the app (through arcana-llama.cpp) and on a
// desktop (tools/reading-cli) when a model is being tried out.
//
// A reading is a short conversation. Each user turn names one card and asks for a few sentences
// about it; the reply is sampled a token at a time so it can be shown as it is written and
// stopped between tokens. The prompt is fed in pieces for the same reason: nothing here runs for
// long, so cancelling never has to reach into native code.
//
// One session, one thread: llama.cpp's context is not safe to share.

#pragma once

#include <cstdint>
#include <string>
#include <utility>
#include <vector>

#include "llama.h"

namespace arcana {

class Session {
public:
    // Returns nullptr if the file is not a model llama.cpp can run.
    static Session *load(const char *path, int n_ctx, int threads, int batch_threads);
    ~Session();
    Session(const Session &) = delete;
    Session &operator=(const Session &) = delete;

    // Starts a new conversation: forgets the last one and sets how the replies are sampled.
    void begin(const std::string &system, float temperature, int top_k, float top_p, float repeat_penalty,
               uint32_t seed = LLAMA_DEFAULT_SEED);

    // Adds what the user says next and opens the model's reply. Returns how many tokens are
    // waiting to be fed, CONTEXT_FULL if the conversation no longer fits with `reserve` tokens
    // left for the reply, or UNREADABLE if the text could not be tokenized.
    int user(const std::string &text, int reserve);

    // Feeds up to `max_tokens` of what is waiting. Returns how many are still waiting, or FAILED.
    int feed(int max_tokens);

    // Sets the rules the next reply must follow, in llama.cpp's GBNF. Empty clears them. A grammar
    // makes anything outside the wanted shape impossible to write, rather than merely discouraged.
    bool grammar(const std::string &gbnf);

    // Writes one more token of the reply into `piece`, as UTF-8 bytes that may end partway
    // through a character. False when the reply is finished: the model ended it, the grammar is
    // complete, or the context is full.
    bool next(std::string &piece);

    // Records what the model replied, so the next turn is formatted after it.
    void reply(const std::string &text);

    int context_left() const;

    static constexpr int CONTEXT_FULL = -2;
    static constexpr int UNREADABLE = -3;
    static constexpr int FAILED = -4;

private:
    Session() = default;
    std::string format(bool open_reply) const;
    void fill_candidates();
    llama_token sample();

    llama_model *model = nullptr;
    llama_context *ctx = nullptr;
    const llama_vocab *vocab = nullptr;
    llama_sampler *chain = nullptr;
    llama_sampler *rules = nullptr;
    std::string chat_template;
    // Text a reply is made to begin with. Models that can "think" before answering are given an
    // empty thought, so they go straight to the answer.
    std::string opening;

    // The conversation so far, as (role, text).
    std::vector<std::pair<std::string, std::string>> messages;
    // How much of the formatted conversation the model has already been given.
    size_t given = 0;

    std::vector<llama_token> pending;
    size_t fed = 0;
    int used = 0;
    bool fresh = true;
    std::vector<llama_token_data> candidates;
};

}  // namespace arcana
