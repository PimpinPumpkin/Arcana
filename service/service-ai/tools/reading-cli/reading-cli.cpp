// Runs a reading on a desktop with the same code the app runs on a phone: the same chat
// formatting, grammar and sampling. It reads a script written by ReadingScriptSamples (see
// README.md) and prints what the model wrote, with how long it took.

#include <chrono>
#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <fstream>
#include <string>

#include "arcana-session.h"
#include "ggml-backend.h"
#include "nlohmann/json.hpp"

using json = nlohmann::json;
using clock_type = std::chrono::steady_clock;

static double seconds_since(clock_type::time_point start) {
    return std::chrono::duration<double>(clock_type::now() - start).count();
}

int main(int argc, char **argv) {
    if (argc < 3) {
        fprintf(stderr,
                "usage: reading-cli <model.gguf> <script.json> [--temp T] [--top-k K] [--top-p P]\n"
                "                   [--repeat-penalty R] [--seed S] [--threads N] [--batch-threads N]\n"
                "                   [--no-grammar]\n");
        return 2;
    }
    float temperature = 0.7f, top_p = 0.9f, repeat_penalty = 1.08f;
    int top_k = 40, threads = 4, batch_threads = 0;
    uint32_t seed = 1;
    bool use_grammar = true;
    for (int i = 3; i < argc; i++) {
        auto value = [&]() { return i + 1 < argc ? argv[++i] : "0"; };
        if (!strcmp(argv[i], "--temp")) temperature = strtof(value(), nullptr);
        else if (!strcmp(argv[i], "--top-k")) top_k = atoi(value());
        else if (!strcmp(argv[i], "--top-p")) top_p = strtof(value(), nullptr);
        else if (!strcmp(argv[i], "--repeat-penalty")) repeat_penalty = strtof(value(), nullptr);
        else if (!strcmp(argv[i], "--seed")) seed = static_cast<uint32_t>(strtoul(value(), nullptr, 10));
        else if (!strcmp(argv[i], "--threads")) threads = atoi(value());
        else if (!strcmp(argv[i], "--batch-threads")) batch_threads = atoi(value());
        else if (!strcmp(argv[i], "--no-grammar")) use_grammar = false;
        else {
            fprintf(stderr, "unknown option %s\n", argv[i]);
            return 2;
        }
    }

    std::ifstream in(argv[2]);
    if (!in) {
        fprintf(stderr, "cannot read %s\n", argv[2]);
        return 1;
    }
    json script = json::parse(in);

    llama_log_set([](enum ggml_log_level level, const char *text, void *) {
        if (level == GGML_LOG_LEVEL_ERROR) fputs(text, stderr);
    }, nullptr);
    ggml_backend_load_all();
    llama_backend_init();
    auto t_load = clock_type::now();
    arcana::Session *s = arcana::Session::load(argv[1], 4096, threads, batch_threads > 0 ? batch_threads : threads);
    if (!s) {
        fprintf(stderr, "cannot load %s\n", argv[1]);
        return 1;
    }

    const double loading = seconds_since(t_load);
    s->begin(script.value("system", ""), temperature, top_k, top_p, repeat_penalty, seed);
    int prompt_tokens = 0, written = 0;
    double reading = 0, writing = 0;
    bool first = true;
    for (const auto &turn : script["turns"]) {
        auto t0 = clock_type::now();
        // A turn marked "restart" begins a new conversation, as the app does when the context fills.
        if (turn.value("restart", false)) s->begin(script.value("system", ""), temperature, top_k, top_p, repeat_penalty, seed);
        int waiting = s->user(turn.value("prompt", ""), turn.value("maxTokens", 130));
        if (waiting < 0) {
            fprintf(stderr, "turn refused (%d)\n", waiting);
            return 1;
        }
        prompt_tokens += waiting;
        while (waiting > 0) {
            waiting = s->feed(48);
            if (waiting < 0) {
                fprintf(stderr, "feed failed\n");
                return 1;
            }
        }
        reading += seconds_since(t0);

        printf("%s%s\n", first ? "" : "\n", turn.value("heading", "").c_str());
        first = false;
        if (!s->grammar(use_grammar ? turn.value("grammar", "") : "")) {
            fprintf(stderr, "grammar did not parse\n");
            return 1;
        }
        auto t1 = clock_type::now();
        std::string reply, piece;
        int tokens = 0;
        const int limit = turn.value("maxTokens", 130);
        while (tokens < limit && s->next(piece)) {
            reply += piece;
            fputs(piece.c_str(), stdout);
            fflush(stdout);
            tokens++;
        }
        writing += seconds_since(t1);
        written += tokens;
        if (tokens >= limit) printf(" [cut off at %d tokens]", limit);
        printf("\n");
        // What the app does: the reply is recorded trimmed.
        size_t a = reply.find_first_not_of(" \n\t"), b = reply.find_last_not_of(" \n\t");
        s->reply(a == std::string::npos ? "" : reply.substr(a, b - a + 1));
    }
    printf("\n-- loaded in %.1f s, read %d tokens in %.1f s (%.0f/s), wrote %d in %.1f s (%.1f/s), %.0f s in all, %d of context left\n",
           loading, prompt_tokens, reading, reading > 0 ? prompt_tokens / reading : 0.0,
           written, writing, writing > 0 ? written / writing : 0.0, loading + reading + writing, s->context_left());
    delete s;
    llama_backend_free();
    return 0;
}
