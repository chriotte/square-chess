#include <atomic>
#include <chrono>
#include <cstdarg>
#include <cstdio>
#include <cstring>
#include <errno.h>
#include <fcntl.h>
#include <iostream>
#include <mutex>
#include <stdio.h>
#include <unistd.h>

#include "src/bitboard.h"
#include "src/endgame.h"
#include "src/position.h"
#include "src/psqt.h"
#include "src/search.h"
#include "src/syzygy/tbprobe.h"
#include "src/thread.h"
#include "src/tt.h"
#include "src/uci.h"
#include "src/piece.h"
#include "src/variant.h"
#include "src/xboard.h"

#include "sfio.h"
#include "./include/multistockfish_variant/stockfish_variant.h"

// https://jineshkj.wordpress.com/2006/12/22/how-to-capture-stdin-stdout-and-stderr-of-child-program/
#define NUM_PIPES 2
#define PARENT_WRITE_PIPE 0
#define PARENT_READ_PIPE 1
#define READ_FD 0
#define WRITE_FD 1
#define PARENT_READ_FD (pipes[PARENT_READ_PIPE][READ_FD])
#define PARENT_WRITE_FD (pipes[PARENT_WRITE_PIPE][WRITE_FD])
#define CHILD_READ_FD (pipes[PARENT_WRITE_PIPE][READ_FD])
#define CHILD_WRITE_FD (pipes[PARENT_READ_PIPE][WRITE_FD])

static const char *QUITOK = "quitok\n";
static int pipes[NUM_PIPES][2] = {{-1, -1}, {-1, -1}};
// A page at a time, matching the buffer the engine's output stream flushes from.
static char buffer[4096];

// An unfinished tail held back from the previous read, because it might turn out
// to be the start of the quit marker.
static char carry[8];
static size_t carry_length = 0;

// Whether the quit marker has been seen. The output that preceded it in the same
// read is handed over first, so the reader gets the engine's last words before it
// is told to stop.
static bool quit_seen = false;

// None of the three is atomic, because stdout_read() has exactly one caller: the
// reader the Dart side runs for the engine it started. They carry state between
// calls, so a second concurrent reader would splice one read's tail onto
// another's and could be handed the end-of-output signal meant for the first.
// init() resets them for the same reason it drains the pipes: it is the one
// point where no reader can be running.

// ---------------------------------------------------------------------------
// Diagnostics
//
// The engine runs on a thread this library does not own and can wedge in
// places that are invisible from Dart (most notably inside ThreadPool::set(0),
// which joins search threads and never returns if one of them ignored the stop
// flag). These few atomics are the only way to tell, from the outside, whether
// a start that never completed is stuck booting, stuck in the UCI loop, or
// stuck shutting down.
//
// Every value here is written by the engine thread and read by Dart, so all of
// it is atomic. Steps are string literals, which have static storage duration
// and so stay valid for any reader.
// ---------------------------------------------------------------------------

static std::atomic<int> g_phase{SF_PHASE_IDLE};
static std::atomic<const char *> g_step{"idle"};
static std::atomic<long long> g_phase_since_ms{0};

// True between entering stockfish_variant_main() and returning from it. This is
// the re-entry guard: the engine's mutable state (Threads, TT, Options,
// variants, pieceMap) is process-global, so a second concurrent run would race
// the first over the very objects it is tearing down.
static std::atomic<bool> g_running{false};

// Whether the pipes have been created. They are created once and reused, so a
// restart neither leaks descriptors nor invalidates the ones the engine's
// streams are bound to.
static std::atomic<bool> g_pipes_ready{false};

static std::mutex g_error_mutex;
static char g_last_error[512] = {0};

static long long steady_now_ms()
{
  using namespace std::chrono;
  return duration_cast<milliseconds>(steady_clock::now().time_since_epoch()).count();
}

static void set_phase(int phase, const char *step)
{
  g_step.store(step, std::memory_order_relaxed);
  g_phase.store(phase, std::memory_order_relaxed);
  g_phase_since_ms.store(steady_now_ms(), std::memory_order_relaxed);
}

static void set_step(const char *step)
{
  g_step.store(step, std::memory_order_relaxed);
  g_phase_since_ms.store(steady_now_ms(), std::memory_order_relaxed);
}

static void set_error(const char *format, ...)
{
  std::lock_guard<std::mutex> lock(g_error_mutex);
  va_list args;
  va_start(args, format);
  vsnprintf(g_last_error, sizeof(g_last_error), format, args);
  va_end(args);
}

// Writes the quit marker straight to the pipe rather than through the engine's
// output stream, so that it still reaches the reader on paths where that stream
// was never bound. Flushes the stream first to preserve ordering on the paths
// where it was.
static void signal_quit()
{
  FairyStockfish::sfio::out() << std::flush;
  if (g_pipes_ready.load(std::memory_order_acquire))
  {
    ssize_t ignored = write(CHILD_WRITE_FD, QUITOK, strlen(QUITOK));
    (void)ignored;
  }
}

// Closes a pipe pair and marks it closed.
//
// Safe on a pair that was never created: the array starts at -1 and every close
// resets it, so this can never close a descriptor it does not own -- which
// matters because 0 is both the array's natural zero value and the process's
// standard input.
static void close_pipe_pair(int which)
{
  for (int end = 0; end < 2; end++)
  {
    if (pipes[which][end] >= 0)
    {
      close(pipes[which][end]);
      pipes[which][end] = -1;
    }
  }
}

// Reads and throws away whatever is currently buffered in a pipe, restoring its
// original blocking mode afterwards. Returns the number of bytes discarded.
static size_t drain_fd(int fd)
{
  const int flags = fcntl(fd, F_GETFL, 0);
  if (flags < 0)
    return 0;

  if (fcntl(fd, F_SETFL, flags | O_NONBLOCK) < 0)
    return 0;

  char scratch[256];
  ssize_t count;
  size_t total = 0;
  while ((count = read(fd, scratch, sizeof(scratch))) > 0)
    total += (size_t)count;

  fcntl(fd, F_SETFL, flags);
  return total;
}

// Empties both pipes before a restart, so that a fresh engine neither reports
// the tail of a dead engine's output as its own greeting, nor executes commands
// that were queued for its predecessor and never consumed.
//
// Only safe because init() refuses to run while an engine is alive: draining
// the input pipe would otherwise steal the running engine's commands.
static void drain_pipes()
{
  const size_t stale_output = drain_fd(PARENT_READ_FD);
  const size_t stale_input = drain_fd(CHILD_READ_FD);

  if (stale_output > 0 || stale_input > 0)
    set_error(
        "init: discarded %zu stale output byte(s) and %zu stale input byte(s) "
        "left over by a previous run",
        stale_output, stale_input);
}

namespace MultiStockfishFairy {
  using namespace FairyStockfish;

  int main(int argc, char* argv[]) {

    sfio::out() << engine_info() << std::endl;

    set_step("piece_map");
    pieceMap.init();
    set_step("variants");
    variants.init();
    set_step("command_line");
    CommandLine::init(argc, argv);
    set_step("uci_options");
    UCI::init(Options);
    set_step("tune");
    Tune::init();
    set_step("psqt");
    PSQT::init(variants.find(Options["UCI_Variant"])->second);
    set_step("bitboards");
    Bitboards::init();
    set_step("position");
    Position::init();
    set_step("bitbases");
    Bitbases::init();
    set_step("endgames");
    Endgames::init();
    set_step("thread_pool");
    Threads.set(size_t(Options["Threads"]));
    set_step("search_clear");
    Search::clear(); // After threads are up
    set_step("nnue");
    Eval::NNUE::init();

    set_phase(SF_PHASE_UCI_LOOP, "uci_loop");
    UCI::loop(argc, argv);

    // Everything past this point is the teardown that can hang: set(0) waits
    // for the search to finish and then joins every thread.
    set_phase(SF_PHASE_SHUTTING_DOWN, "thread_pool_teardown");
    Threads.set(0);
    set_step("variants_teardown");
    variants.clear_all();
    pieceMap.clear_all();
    delete XBoard::stateMachine;
    return 0;
  }
}

int stockfish_variant_init()
{
  if (g_running.load(std::memory_order_acquire))
  {
    set_error(
        "init: refused, the previous engine is still running (phase=%d step=%s for %lldms). "
        "It never returned from stockfish_variant_main(); this process cannot host another engine.",
        g_phase.load(std::memory_order_relaxed),
        g_step.load(std::memory_order_relaxed),
        steady_now_ms() - g_phase_since_ms.load(std::memory_order_relaxed));
    return SF_INIT_ALREADY_RUNNING;
  }

  // A reader that was killed rather than allowed to finish leaves its carry and
  // its end-of-output flag behind. Left set, the flag would tell the *next*
  // engine's reader to stop before it had read a byte.
  carry_length = 0;
  quit_seen = false;

  if (g_pipes_ready.load(std::memory_order_acquire))
  {
    // Reuse the existing pipes rather than creating a second pair, which is
    // what this used to do on every start: the previous parent-side
    // descriptors were simply overwritten in the array, leaking two per
    // restart. Reusing them also keeps one stable channel for the lifetime of
    // the process, so the descriptors the engine's streams are bound to always
    // refer to the pipe this library is actually reading and writing.
    drain_pipes();
    set_phase(SF_PHASE_INITIALIZED, "pipes_reused");
    return 0;
  }

  set_phase(SF_PHASE_INITIALIZING, "creating_pipes");

  if (pipe(pipes[PARENT_READ_PIPE]) != 0)
  {
    set_error("init: pipe(PARENT_READ_PIPE) failed: %s", strerror(errno));
    close_pipe_pair(PARENT_READ_PIPE);
    set_phase(SF_PHASE_FAILED, "pipe_failed");
    return SF_INIT_PIPE_FAILED;
  }

  if (pipe(pipes[PARENT_WRITE_PIPE]) != 0)
  {
    set_error("init: pipe(PARENT_WRITE_PIPE) failed: %s", strerror(errno));
    close_pipe_pair(PARENT_READ_PIPE);
    close_pipe_pair(PARENT_WRITE_PIPE);
    set_phase(SF_PHASE_FAILED, "pipe_failed");
    return SF_INIT_PIPE_FAILED;
  }

  // The write side must never block: it is driven from the caller's isolate,
  // which on Flutter is usually the platform isolate. A full pipe means the
  // engine has stopped reading, and blocking there would freeze the app.
  const int flags = fcntl(PARENT_WRITE_FD, F_GETFL, 0);
  if (flags < 0 || fcntl(PARENT_WRITE_FD, F_SETFL, flags | O_NONBLOCK) < 0)
  {
    set_error("init: could not make the write pipe non-blocking: %s", strerror(errno));
    // Both pairs are open and g_pipes_ready stays false, so leaving them would
    // leak four descriptors every time a caller retried init().
    close_pipe_pair(PARENT_READ_PIPE);
    close_pipe_pair(PARENT_WRITE_PIPE);
    set_phase(SF_PHASE_FAILED, "fcntl_failed");
    return SF_INIT_FCNTL_FAILED;
  }

  g_pipes_ready.store(true, std::memory_order_release);
  set_phase(SF_PHASE_INITIALIZED, "pipes_created");
  return 0;
}

int stockfish_variant_main()
{
  if (!g_pipes_ready.load(std::memory_order_acquire))
  {
    set_error("main: called before a successful stockfish_variant_init()");
    set_phase(SF_PHASE_FAILED, "not_initialized");
    return SF_MAIN_NOT_INITIALIZED;
  }

  bool expected = false;
  if (!g_running.compare_exchange_strong(expected, true))
  {
    // Fairy-Stockfish keeps Threads, TT, Options, variants and pieceMap as
    // process globals. Running a second engine over them while the first is
    // still alive is undefined behaviour, so refuse instead.
    set_error(
        "main: refused, an engine is already running (phase=%d step=%s for %lldms)",
        g_phase.load(std::memory_order_relaxed),
        g_step.load(std::memory_order_relaxed),
        steady_now_ms() - g_phase_since_ms.load(std::memory_order_relaxed));
    return SF_MAIN_ALREADY_RUNNING;
  }

  set_phase(SF_PHASE_REDIRECTING, "bind_streams");

  // The engine reads and writes streams this library owns, so this points them
  // at its own end of the pipe. It replaces a dup2 onto fd 0 and fd 1, which
  // took over the whole process's standard descriptors: only one flavour could
  // hold them at a time, and the host application lost its own stdout for as
  // long as an engine was running.
  if (!FairyStockfish::sfio::bind(CHILD_READ_FD, CHILD_WRITE_FD))
  {
    set_error("main: could not bind the engine's streams to the pipe descriptors");
    set_phase(SF_PHASE_FAILED, "bind_failed");
    signal_quit();
    g_running.store(false, std::memory_order_release);
    return SF_MAIN_DUP2_FAILED;
  }

  // The child-side descriptors stay open for the lifetime of the process: the
  // streams bound above write and read them directly, and keeping them means a
  // later restart can rebind without recreating the pipes.

  set_phase(SF_PHASE_ENGINE_BOOTING, "engine_boot");

  int argc = 1;
  char *argv[] = {""};
  int exitCode;

#if defined(__cpp_exceptions)
  try
  {
    exitCode = MultiStockfishFairy::main(argc, argv);
  }
  catch (const std::exception &e)
  {
    set_error("main: the engine threw during '%s': %s", g_step.load(std::memory_order_relaxed), e.what());
    set_phase(SF_PHASE_FAILED, "engine_threw");
    signal_quit();
    g_running.store(false, std::memory_order_release);
    return SF_MAIN_ENGINE_THREW;
  }
  catch (...)
  {
    set_error("main: the engine threw an unknown exception during '%s'", g_step.load(std::memory_order_relaxed));
    set_phase(SF_PHASE_FAILED, "engine_threw");
    signal_quit();
    g_running.store(false, std::memory_order_release);
    return SF_MAIN_ENGINE_THREW;
  }
#else
  exitCode = MultiStockfishFairy::main(argc, argv);
#endif

  set_phase(exitCode == 0 ? SF_PHASE_EXITED : SF_PHASE_FAILED, "exited");
  signal_quit();
  g_running.store(false, std::memory_order_release);

  return exitCode;
}

ssize_t stockfish_variant_stdin_write(char *data)
{
  if (!g_pipes_ready.load(std::memory_order_acquire))
  {
    set_error("stdin_write: called before a successful stockfish_variant_init()");
    return SF_WRITE_NOT_INITIALIZED;
  }

  const size_t length = strlen(data);
  size_t written = 0;

  // The descriptor is non-blocking, so a full pipe surfaces as EAGAIN rather
  // than a hang. Retry briefly to ride out a busy engine, then give up and say
  // so: a pipe that stays full means the engine has stopped reading.
  const long long deadline = steady_now_ms() + 250;

  while (written < length)
  {
    const ssize_t count = write(PARENT_WRITE_FD, data + written, length - written);

    if (count > 0)
    {
      written += (size_t)count;
      continue;
    }

    if (count < 0 && errno == EINTR)
      continue;

    if (count < 0 && (errno == EAGAIN || errno == EWOULDBLOCK))
    {
      if (steady_now_ms() >= deadline)
      {
        set_error(
            "stdin_write: the input pipe stayed full for 250ms, the engine is not reading "
            "(%zu of %zu bytes written, phase=%d step=%s)",
            written, length,
            g_phase.load(std::memory_order_relaxed),
            g_step.load(std::memory_order_relaxed));
        // A partial write has already corrupted the command stream; the caller
        // needs to know the difference.
        return written > 0 ? SF_WRITE_PARTIAL : SF_WRITE_PIPE_FULL;
      }
      usleep(2000);
      continue;
    }

    set_error("stdin_write: write failed after %zu of %zu bytes: %s", written, length, strerror(errno));
    return written > 0 ? SF_WRITE_PARTIAL : SF_WRITE_FAILED;
  }

  return (ssize_t)written;
}

// The length of the longest suffix of [data] that is a proper prefix of QUITOK.
//
// The marker is the last thing an engine writes, but a read can return it glued
// to the output before it, and -- if the pipe happened to be full -- split across
// two reads. Holding such a tail back is safe: every line the engine writes ends
// in '\n', which no prefix of the marker does, so an unfinished tail always has
// more data coming after it.
static size_t partial_quit_length(const char *data, size_t length)
{
  const size_t marker = strlen(QUITOK);
  size_t candidate = (marker - 1 < length) ? marker - 1 : length;

  for (; candidate > 0; candidate--)
  {
    if (memcmp(data + length - candidate, QUITOK, candidate) == 0)
      return candidate;
  }

  return 0;
}

char *stockfish_variant_stdout_read()
{
  if (!g_pipes_ready.load(std::memory_order_acquire))
  {
    set_error("stdout_read: called before a successful stockfish_variant_init()");
    return NULL;
  }

  // The marker was found last time and everything before it has been delivered.
  // The engine is gone; tell the reader to stop.
  if (quit_seen)
  {
    quit_seen = false;
    carry_length = 0;
    return NULL;
  }

  const size_t marker = strlen(QUITOK);

  for (;;)
  {
    memcpy(buffer, carry, carry_length);

    const ssize_t count = read(PARENT_READ_FD, buffer + carry_length, sizeof(buffer) - 1 - carry_length);

    if (count < 0)
    {
      set_error("stdout_read: read failed: %s", strerror(errno));
      carry_length = 0;
      return NULL;
    }

    // End of file. Returning the empty buffer here would spin the reader.
    if (count == 0)
    {
      set_error("stdout_read: the output pipe reached end of file");
      carry_length = 0;
      return NULL;
    }

    size_t total = carry_length + (size_t)count;
    carry_length = 0;
    buffer[total] = 0;

    // The marker is written on its own once the engine's output has been
    // flushed, so it is always last -- but this read may have picked up output
    // written before it.
    if (total >= marker && memcmp(buffer + total - marker, QUITOK, marker) == 0)
    {
      total -= marker;
      buffer[total] = 0;
      if (total == 0)
        return NULL;

      quit_seen = true;
      return buffer;
    }

    const size_t partial = partial_quit_length(buffer, total);
    if (partial > 0)
    {
      memcpy(carry, buffer + total - partial, partial);
      carry_length = partial;
      total -= partial;
      buffer[total] = 0;
    }

    // The whole read was an unfinished tail. Wait for the rest of it rather than
    // handing the reader an empty string.
    if (total == 0)
      continue;

    return buffer;
  }
}

int stockfish_variant_phase()
{
  return g_phase.load(std::memory_order_relaxed);
}

const char *stockfish_variant_phase_step()
{
  return g_step.load(std::memory_order_relaxed);
}

long long stockfish_variant_phase_elapsed_ms()
{
  const long long since = g_phase_since_ms.load(std::memory_order_relaxed);
  return since == 0 ? 0 : steady_now_ms() - since;
}

int stockfish_variant_last_error(char *buffer, int size)
{
  if (buffer == NULL || size <= 0)
    return 0;

  // The copy happens under the lock and into the caller's own buffer. Returning
  // a pointer to a shared buffer instead would race: the caller reads it after
  // the lock is gone, so a concurrent reader could overwrite the bytes -- and
  // the terminator -- while the first one was still scanning them.
  std::lock_guard<std::mutex> lock(g_error_mutex);

  const size_t length = strnlen(g_last_error, sizeof(g_last_error));
  if (length == 0)
  {
    buffer[0] = 0;
    return 0;
  }

  const size_t limit = (size_t)size - 1;
  const size_t copied = length < limit ? length : limit;
  memcpy(buffer, g_last_error, copied);
  buffer[copied] = 0;
  return (int)copied;
}
