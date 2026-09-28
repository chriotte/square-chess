#include <jni.h>
#include <memory>
#include <mutex>
#include <sstream>
#include "attacks.h"
#include "engine.h"
#include "position.h"
#include "search.h"
#include "misc.h"

using namespace Stockfish;
static std::shared_ptr<Engine> engine;
static std::mutex handleMutex;
static std::once_flag initFlag;
static std::mutex metricsMutex;
static std::string lastMetrics;
static std::string str(JNIEnv* env, jstring value) {
    const char* chars = env->GetStringUTFChars(value, nullptr);
    std::string result(chars); env->ReleaseStringUTFChars(value, chars); return result;
}
static void option(Engine& e, const std::string& name, const std::string& value) {
    std::istringstream command("name " + name + " value " + value);
    e.get_options().setoption(command);
}
extern "C" JNIEXPORT void JNICALL
Java_com_chriotte_squarechess_NativeEngine_start(JNIEnv* env, jobject, jstring path) {
    std::call_once(initFlag, [] { Attacks::init(); Position::init(); });
    auto e = std::make_shared<Engine>();
    e->set_on_update_no_moves([](const Engine::InfoShort&){});
    e->set_on_update_full([](const Engine::InfoFull& info){
        std::lock_guard<std::mutex> lock(metricsMutex);
        lastMetrics="info depth "+std::to_string(info.depth)+" nodes "+std::to_string(info.nodes)+" time "+std::to_string(info.timeMs);
    });
    e->set_on_iter([](const Engine::InfoIter&){});
    e->set_on_start([](){});
    e->set_on_verify_network([](std::string_view){});
    option(*e, "EvalFile", str(env,path));
    option(*e, "Hash", "16");
    std::lock_guard<std::mutex> guard(handleMutex); engine = e;
}
extern "C" JNIEXPORT jstring JNICALL
Java_com_chriotte_squarechess_NativeEngine_search(JNIEnv* env, jobject, jstring fen, jstring moves, jint level, jint millis) {
    std::shared_ptr<Engine> e;
    { std::lock_guard<std::mutex> guard(handleMutex); e = engine; }
    if (!e) return env->NewStringUTF("error:not started");
    std::vector<std::string> history; std::istringstream input(str(env,moves)); std::string word;
    while(input >> word) history.push_back(word);
    if (e->set_position(str(env,fen), history)) return env->NewStringUTF("error:invalid position");
    option(*e, "Skill Level", std::to_string(std::max(0,std::min(20,(level-1)*2))));
    std::string best;
    e->set_on_bestmove([&best](std::string_view move,std::string_view){ best = move; });
    Search::LimitsType limits; limits.startTime = now(); limits.movetime = std::max(50,std::min(3000,(int)millis));
    e->go(limits); e->wait_for_search_finished();
    e->set_on_bestmove([](std::string_view,std::string_view){});
    return env->NewStringUTF(best.c_str());
}
extern "C" JNIEXPORT void JNICALL
Java_com_chriotte_squarechess_NativeEngine_stop(JNIEnv*, jobject) {
    std::lock_guard<std::mutex> guard(handleMutex); if(engine) engine->stop();
}
extern "C" JNIEXPORT void JNICALL
Java_com_chriotte_squarechess_NativeEngine_newGame(JNIEnv*, jobject) {
    std::shared_ptr<Engine> e; { std::lock_guard<std::mutex> guard(handleMutex); e=engine; }
    if(e) e->search_clear();
}
extern "C" JNIEXPORT void JNICALL
Java_com_chriotte_squarechess_NativeEngine_close(JNIEnv*, jobject) {
    std::shared_ptr<Engine> e; { std::lock_guard<std::mutex> guard(handleMutex); e.swap(engine); }
    if(e) { e->stop(); e->wait_for_search_finished(); }
}
extern "C" JNIEXPORT jstring JNICALL
Java_com_chriotte_squarechess_NativeEngine_metrics(JNIEnv* env,jobject) {
    std::lock_guard<std::mutex> lock(metricsMutex);return env->NewStringUTF(lastMetrics.c_str());
}
extern "C" JNIEXPORT jstring JNICALL
Java_com_chriotte_squarechess_NativeEngine_capabilities(JNIEnv* env,jobject) {
    std::lock_guard<std::mutex> lock(handleMutex);std::ostringstream out;
    if(engine) out<<engine->get_options();return env->NewStringUTF(out.str().c_str());
}
