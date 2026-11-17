#include <jni.h>
#include <string>
#include <vector>
#include <algorithm>
#include <unordered_set>
#include <cctype>
#include "pinyin/include/spellingtrie.h"
#include "pinyin/include/pinyinime.h"
#include "pinyin/include/matrixsearch.h"
extern "C" ime_pinyin::MatrixSearch* matrix_search;
using namespace ime_pinyin;
static bool ready = false;
static std::string str(JNIEnv* env, jstring value) {
    const char* chars = env->GetStringUTFChars(value, nullptr);
    std::string result(chars); env->ReleaseStringUTFChars(value, chars); return result;
}
static jobjectArray array(JNIEnv* env, const std::vector<std::u16string>& items) {
    auto result = env->NewObjectArray(items.size(), env->FindClass("java/lang/String"), nullptr);
    for (size_t i = 0; i < items.size(); ++i) {
        auto s = env->NewString(reinterpret_cast<const jchar*>(items[i].data()), items[i].size());
        env->SetObjectArrayElement(result, i, s); env->DeleteLocalRef(s);
    }
    return result;
}
extern "C" JNIEXPORT jboolean JNICALL Java_app_echo_pinyin_NativePinyin_open(JNIEnv* env, jobject, jstring dict, jstring user) {
    ready = im_open_decoder(str(env, dict).c_str(), str(env, user).c_str());
    if (ready) { im_set_max_lens(32, 16); im_enable_shm_as_szm(true); }
    return ready;
}
struct Path { std::string spelling; float score; };
static std::string cached_digits;
static std::string cached_prefix;
static std::vector<std::vector<Path>> beams(1, {{"", 0}});
static std::vector<Path> paths(const std::string& digits, const std::string& prefix = "") {
    std::string fixed;
    std::vector<bool> boundary;
    for (char c : prefix) {
        if (c == '\'' && !boundary.empty()) boundary.back() = true;
        else if (c >= 'a' && c <= 'z') { fixed += c; boundary.push_back(false); }
        else return {};
    }
    if (cached_prefix != prefix) { cached_digits.clear(); beams = {{{"", 0}}}; }
    cached_prefix = prefix;
    size_t same = 0;
    while (same < cached_digits.size() && same < digits.size() && cached_digits[same] == digits[same]) ++same;
    beams.resize(same + 1);
    static const char* letters[] = {"", "", "abc", "def", "ghi", "jkl", "mno", "pqrs", "tuv", "wxyz"};
    for (size_t pos = same; pos < digits.size(); ++pos) {
        std::vector<Path> next;
        if (digits[pos] < '2' || digits[pos] > '9') return {};
        for (const auto& path : beams.back()) for (char c : std::string(letters[digits[pos] - '0'])) {
            if (pos < fixed.size() && c != fixed[pos]) continue;
            auto py = path.spelling;
            if (pos > 0 && pos <= boundary.size() && boundary[pos - 1]) py += '\'';
            py += c;
            im_reset_search();
            if (!im_search(py.c_str(), py.size())) continue;
            size_t decoded = 0; im_get_sps_str(&decoded);
            if (decoded == py.size()) next.push_back({py, matrix_search->echo_score()});
        }
        std::stable_sort(next.begin(), next.end(), [](const Path& a, const Path& b) { return a.score < b.score; });
        if (next.size() > 64) next.resize(64);
        beams.push_back(std::move(next));
    }
    cached_digits = digits;
    return beams.back();
}
static jobjectArray query(JNIEnv* env, jstring value, bool t9, const std::string& prefix = "") {
    std::vector<std::u16string> result;
    if (!ready) return array(env, result);
    auto input = str(env, value);
    auto options = t9 ? paths(input, prefix) : std::vector<Path>{{input, 0}};
    std::unordered_set<std::u16string> seen;
    for (const auto& path : options) {
        im_reset_search();
        size_t count = im_search(path.spelling.c_str(), path.spelling.size());
        size_t decoded = 0; im_get_sps_str(&decoded);
        if (decoded != path.spelling.size()) continue;
        const uint16* starts = nullptr;
        size_t syllables = im_get_spl_start_pos(starts);
        for (size_t i = 0; i < std::min(count, size_t(t9 ? 8 : 60)); ++i) {
            char16 buf[128] = {};
            if (!im_get_candidate(i, buf, 128)) continue;
            std::u16string word(reinterpret_cast<char16_t*>(buf));
            if (word.empty() || !seen.insert(word).second) continue;
            size_t spellingEnd = i == 0 ? path.spelling.size() : starts[std::min(word.size(), syllables)];
            size_t consumed = t9 ? std::count_if(path.spelling.begin(), path.spelling.begin() + spellingEnd, [](char c) { return c != '\''; }) : spellingEnd;
            std::string segmented;
            for (size_t s = 0; s < syllables; ++s) {
                if (!segmented.empty()) segmented += '\'';
                for (size_t j = starts[s]; j < starts[s+1]; ++j) if (path.spelling[j] != '\'') segmented += path.spelling[j];
            }
            std::string meta = segmented + "\t" + std::to_string(consumed) + "\t";
            std::u16string item(meta.begin(), meta.end()); item += word;
            result.push_back(item);
        }
        if (result.size() >= 60) break;
    }
    return array(env, result);
}
extern "C" JNIEXPORT jobjectArray JNICALL Java_app_echo_pinyin_NativePinyin_query(JNIEnv* env, jobject, jstring value, jboolean t9) { return query(env,value,t9); }
extern "C" JNIEXPORT jobjectArray JNICALL Java_app_echo_pinyin_NativePinyin_queryT9(JNIEnv* env, jobject, jstring value, jstring prefix) { return query(env,value,true,str(env,prefix)); }
extern "C" JNIEXPORT jobjectArray JNICALL Java_app_echo_pinyin_NativePinyin_syllables(JNIEnv* env, jobject) {
    std::vector<std::u16string> result;
    if (!ready) return array(env,result);
    auto& trie = SpellingTrie::get_instance();
    for (size_t id = kFullSplIdStart; id < kFullSplIdStart + trie.get_spelling_num(); ++id) {
        const char* value = trie.get_spelling_str(id);
        std::u16string item;
        for (; value && *value; ++value) item += char16_t(std::tolower(static_cast<unsigned char>(*value)));
        if (!item.empty()) result.push_back(item);
    }
    return array(env,result);
}
extern "C" JNIEXPORT jobjectArray JNICALL Java_app_echo_pinyin_NativePinyin_predict(JNIEnv* env, jobject, jstring history) {
    std::vector<std::u16string> result;
    if (!ready) return array(env, result);
    auto size = env->GetStringLength(history);
    auto chars = env->GetStringChars(history, nullptr);
    char16 buf[128] = {};
    for (int i = 0; i < std::min(int(size), 127); ++i) buf[i] = chars[i];
    env->ReleaseStringChars(history, chars);
    char16 (*predictions)[kMaxPredictSize + 1] = nullptr;
    size_t count = im_get_predicts(buf, predictions);
    for (size_t i = 0; i < std::min(count, size_t(30)); ++i) result.emplace_back(reinterpret_cast<char16_t*>(predictions[i]));
    return array(env, result);
}
extern "C" JNIEXPORT void JNICALL Java_app_echo_pinyin_NativePinyin_close(JNIEnv*, jobject) {
    if (ready) im_close_decoder(); ready = false;
    cached_digits.clear(); cached_prefix.clear(); beams = {{{"", 0}}};
}
