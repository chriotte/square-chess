// Square Chess experiment: JNI adapter to the pinned Lichess UCI pipe wrapper.
// GPL-3.0-or-later. No move selection is implemented here.
#include <jni.h>
#include <atomic>
#include <chrono>
#include <condition_variable>
#include <deque>
#include <mutex>
#include <sstream>
#include <stdexcept>
#include <string>
#include <thread>
#include "fairy/include/multistockfish_variant/stockfish_variant.h"
#include "fairy/src/uci.h"

namespace {
std::mutex operationMutex, writeMutex, outputMutex;
std::condition_variable outputReady;
std::deque<std::string> lines;
std::thread engineThread, readerThread;
bool running=false, ended=false;
std::atomic<unsigned> stopEpoch{0};
std::string capabilities, metrics;
std::string text(JNIEnv* env,jstring value) {
    const char* raw=env->GetStringUTFChars(value,nullptr);
    std::string result(raw);env->ReleaseStringUTFChars(value,raw);return result;
}
void send(const std::string& command) {
    std::lock_guard<std::mutex> guard(writeMutex);
    std::string message=command+"\n";
    if(stockfish_variant_stdin_write(message.data())!=static_cast<ssize_t>(message.size()))
        throw std::runtime_error("Fairy UCI write failed");
}
std::string readUntil(const std::string& prefix,int timeoutMs,std::string* transcript=nullptr) {
    auto deadline=std::chrono::steady_clock::now()+std::chrono::milliseconds(timeoutMs);
    std::unique_lock<std::mutex> lock(outputMutex);
    for(;;) {
        if(!outputReady.wait_until(lock,deadline,[]{return !lines.empty() || ended;}))
            throw std::runtime_error("Fairy UCI timeout waiting for "+prefix);
        if(lines.empty()) throw std::runtime_error("Fairy engine exited before "+prefix);
        std::string line=std::move(lines.front());lines.pop_front();
        if(transcript) *transcript+=line+"\n";
        if(line.rfind(prefix,0)==0) return line;
    }
}
void reader() {
    std::string pending;
    while(char* chunk=stockfish_variant_stdout_read()) {
        pending+=chunk;
        std::lock_guard<std::mutex> lock(outputMutex);
        size_t end;
        while((end=pending.find('\n'))!=std::string::npos) {
            lines.push_back(pending.substr(0,end));pending.erase(0,end+1);
        }
        outputReady.notify_all();
    }
    std::lock_guard<std::mutex> lock(outputMutex);ended=true;outputReady.notify_all();
}
void option(const std::string& name,const std::string& value) {send("setoption name "+name+" value "+value);}
void throwJava(JNIEnv* env,const std::exception& error) {
    env->ThrowNew(env->FindClass("java/lang/IllegalStateException"),error.what());
}
void shutdown() {
    if(!running) return;
    send("stop");send("quit");
    if(engineThread.joinable()) engineThread.join();
    if(readerThread.joinable()) readerThread.join();
    running=false;
}
}
extern "C" JNIEXPORT void JNICALL Java_com_chriotte_squarechess_NativeEngine_start(JNIEnv* env,jobject,jstring) {
    std::lock_guard<std::mutex> guard(operationMutex);
    try {
        if(running) return;
        if(stockfish_variant_init()!=0) throw std::runtime_error("Fairy pipe initialisation failed");
        {std::lock_guard<std::mutex> lock(outputMutex);lines.clear();ended=false;}
        running=true;
        readerThread=std::thread(reader);
        engineThread=std::thread([]{stockfish_variant_main();});
        capabilities.clear();send("uci");readUntil("uciok",10000,&capabilities);
        if(capabilities.find("option name Skill Level type spin default 20 min -20 max 20")==std::string::npos)
            throw std::runtime_error("Unexpected Fairy Skill Level range");
        option("UCI_Variant","chess");option("UCI_Chess960","false");
        option("Threads","1");option("Hash","16");option("Ponder","false");
        option("Use NNUE","false");option("UCI_LimitStrength","false");option("MultiPV","8");
        send("isready");readUntil("readyok",10000);
    } catch(const std::exception& e) {try{shutdown();}catch(...){} throwJava(env,e);}
}
extern "C" JNIEXPORT jstring JNICALL Java_com_chriotte_squarechess_NativeEngine_search(JNIEnv* env,jobject,jstring fen,jstring moves,jint level,jint millis) {
    std::lock_guard<std::mutex> guard(operationMutex);
    try {
        if(!running) throw std::runtime_error("Fairy is not started");
        const auto epoch=stopEpoch.load();
        // Stable saved-game IDs: A-D, test-only control, then E-H.
        const int skills[]={-18,-14,-10,-6,20,0,4,8,12};
        if(level<1 || level>9) throw std::runtime_error("Unknown Fairy experimental profile");
        option("UCI_LimitStrength","false");option("Skill Level",std::to_string(skills[level-1]));
        option("MultiPV","8");
        const auto history=text(env,moves);
        send("position fen "+text(env,fen)+(history.empty()?"":" moves "+history));
        if(epoch!=stopEpoch.load()) return env->NewStringUTF("error:cancelled");
        metrics.clear();
        send("go movetime "+std::to_string(millis));
        if(epoch!=stopEpoch.load()) send("stop");
        auto best=readUntil("bestmove ",millis+10000,&metrics);
        std::istringstream parser(best);std::string label,move;parser>>label>>move;
        return env->NewStringUTF(move.c_str());
    } catch(const std::exception& e) {try{send("stop");}catch(...){} throwJava(env,e);return nullptr;}
}
extern "C" JNIEXPORT void JNICALL Java_com_chriotte_squarechess_NativeEngine_stop(JNIEnv* env,jobject) {
    ++stopEpoch;
    try {if(stockfish_variant_phase()==SF_PHASE_UCI_LOOP) send("stop");}catch(const std::exception& e){throwJava(env,e);}
}
extern "C" JNIEXPORT void JNICALL Java_com_chriotte_squarechess_NativeEngine_newGame(JNIEnv* env,jobject) {
    std::lock_guard<std::mutex> guard(operationMutex);
    try {if(running){send("ucinewgame");send("isready");readUntil("readyok",10000);}}catch(const std::exception& e){throwJava(env,e);}
}
extern "C" JNIEXPORT void JNICALL Java_com_chriotte_squarechess_NativeEngine_close(JNIEnv* env,jobject) {
    std::lock_guard<std::mutex> guard(operationMutex);
    try {shutdown();}catch(const std::exception& e){throwJava(env,e);}
}
extern "C" JNIEXPORT jstring JNICALL Java_com_chriotte_squarechess_NativeEngine_capabilities(JNIEnv* env,jobject) {
    std::lock_guard<std::mutex> guard(operationMutex);return env->NewStringUTF(capabilities.c_str());
}
extern "C" JNIEXPORT jstring JNICALL Java_com_chriotte_squarechess_NativeEngine_metrics(JNIEnv* env,jobject) {
    std::lock_guard<std::mutex> guard(operationMutex);return env->NewStringUTF(metrics.c_str());
}
extern "C" JNIEXPORT jstring JNICALL Java_com_chriotte_squarechess_NativeEngine_configuration(JNIEnv* env,jobject) {
    std::lock_guard<std::mutex> guard(operationMutex);
    using FairyStockfish::Options;
    std::string config="skill="+std::to_string(int(Options["Skill Level"]))+
        ";multipv="+std::to_string(int(Options["MultiPV"]))+
        ";threads="+std::to_string(int(Options["Threads"]))+
        ";hash="+std::to_string(int(Options["Hash"]))+
        ";nnue="+std::to_string(int(Options["Use NNUE"]))+
        ";limitStrength="+std::to_string(int(Options["UCI_LimitStrength"]));
    return env->NewStringUTF(config.c_str());
}
