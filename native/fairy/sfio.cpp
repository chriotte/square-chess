#include "sfio.h"

#include <algorithm>
#include <cerrno>
#include <cstddef>
#include <cstring>
#include <streambuf>

#include <fcntl.h>
#include <unistd.h>

namespace SFIO_NAMESPACE {
namespace sfio {

namespace {

// Writes to a descriptor, buffering a page at a time so that a line of UCI
// output costs one write() rather than one per character. The descriptor is the
// engine's own end of the shim's pipe, and is blocking -- exactly what fd 1 was
// after the dup2 this replaces.
//
// Not internally synchronised, which is safe for the same reason std::cout was:
// every site that writes more than a single token does so between IO_LOCK and
// IO_UNLOCK, and sync_cout is the only way output is produced from a search
// thread.
class FdOutBuf final: public std::streambuf {

 public:
  FdOutBuf() { setp(buf, buf + sizeof(buf)); }

  void set_fd(int descriptor) { fd = descriptor; }

  // Throws away whatever has not been written yet.
  void discard() { setp(buf, buf + sizeof(buf)); }

 protected:
  int_type overflow(int_type c) override {

    if (write_out() < 0)
        return traits_type::eof();

    if (!traits_type::eq_int_type(c, traits_type::eof()))
    {
        *pptr() = traits_type::to_char_type(c);
        pbump(1);
    }

    return traits_type::not_eof(c);
  }

  // std::endl, and therefore sync_endl, ends up here: this is what puts a
  // finished line into the pipe.
  int sync() override { return write_out(); }

 private:
  // Empties the put area onto the descriptor. Returns -1 if a write cannot be
  // completed, which raises badbit on the stream -- the same outcome a failed
  // write to std::cout had. The put area is reset either way, so a broken pipe
  // cannot leave the engine wedged against a permanently full buffer.
  int write_out() {

    const char*       p   = pbase();
    const char* const end = pptr();
    int               result = 0;

    while (p < end)
    {
        const ssize_t written = ::write(fd, p, size_t(end - p));

        if (written > 0)
        {
            p += written;
            continue;
        }

        if (written < 0 && errno == EINTR)
            continue;

        result = -1;
        break;
    }

    setp(buf, buf + sizeof(buf));
    return result;
  }

  char buf[4096];
  int  fd = STDOUT_FILENO;
};


// Reads from a descriptor. The small putback area keeps formatted extraction
// working, which can unget; the UCI loop itself only ever calls getline().
class FdInBuf final: public std::streambuf {

 public:
  FdInBuf() { discard(); }

  void set_fd(int descriptor) { fd = descriptor; }

  // Throws away whatever was read ahead of the engine but not yet consumed.
  void discard() { setg(buf + Putback, buf + Putback, buf + Putback); }

 protected:
  int_type underflow() override {

    if (gptr() < egptr())
        return traits_type::to_int_type(*gptr());

    // Carry the most recently consumed characters over, so that they stay
    // available for putback across a refill.
    const size_t kept = std::min(size_t(gptr() - eback()), Putback);
    std::memmove(buf + Putback - kept, gptr() - kept, kept);

    ssize_t count;
    do
        count = ::read(fd, buf + Putback, sizeof(buf) - Putback);
    while (count < 0 && errno == EINTR);

    // End of file, or a descriptor that cannot be read. Either way this engine
    // has no further input, and the UCI loop treats that exactly as a "quit".
    if (count <= 0)
        return traits_type::eof();

    setg(buf + Putback - kept, buf + Putback, buf + Putback + size_t(count));
    return traits_type::to_int_type(*gptr());
  }

 private:
  static constexpr size_t Putback = 8;

  char buf[4096];
  int  fd = STDIN_FILENO;
};


// The two streams and the buffers behind them.
//
// Deliberately never destroyed: a search thread can still be writing when the
// process runs its static destructors, and a destroyed stream is a worse outcome
// than a leaked one. It is the same reason std::cout outlives every other static
// object in the program.
struct Channel {

  FdOutBuf     outbuf;
  FdInBuf      inbuf;
  std::ostream out{&outbuf};
  std::istream in{&inbuf};

  Channel() {
      // Mirrors std::cin's tie to std::cout, which upstream depends on: lines
      // written with '\n' rather than a flush -- "info string" among them --
      // only reach the GUI because the next read pushes them out.
      in.tie(&out);
  }
};

Channel& channel() {
  static Channel* const instance = new Channel();
  return *instance;
}

}  // namespace


std::istream& in() { return channel().in; }

std::ostream& out() { return channel().out; }

bool bind(int read_fd, int write_fd) {

  if (read_fd < 0 || write_fd < 0)
      return false;

  // A closed descriptor would send this engine's output to whatever the process
  // opens on that number next, so refuse before committing to it.
  if (::fcntl(read_fd, F_GETFD) == -1 || ::fcntl(write_fd, F_GETFD) == -1)
      return false;

  Channel& c = channel();

  c.outbuf.set_fd(write_fd);
  c.inbuf.set_fd(read_fd);

  // The shim drains the pipes themselves before a restart, but bytes a previous
  // engine had already pulled into these buffers are invisible to it.
  c.outbuf.discard();
  c.inbuf.discard();

  c.out.clear();
  c.in.clear();

  return true;
}

}  // namespace sfio
}  // namespace SFIO_NAMESPACE
