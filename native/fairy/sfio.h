/*
  Private engine I/O.

  Every flavour of this plugin is a separate library with its own copy of the
  engine's state, but each of them used to talk to the outside world through the
  process's own descriptors: the shim dup2()'d its pipe onto fd 0 and fd 1, and
  the engine read std::cin and wrote std::cout. That had two consequences. Two
  flavours could not be resident at once -- the second redirection won, and both
  engines' output arrived in one channel -- and the host application lost its own
  stdout for as long as an engine was running.

  These two streams replace std::cin and std::cout inside the engine. The shim
  binds them to its pipe with bind(), in place of the dup2 it used to do, so this
  engine's input and output reach this engine's pipe and nothing else's.

  Until bind() is called they read and write the process's standard descriptors,
  which keeps a plain command-line build of the engine working unchanged.

  This file and sfio.cpp are identical in all three native packages. Only
  sfio_config.h differs, and the namespace it names is what keeps each flavour's
  streams its own. The engine's misc.h declares in() and out() a second time,
  inside the engine namespace, so that sync_cout can reach them without the
  vendored sources needing to include anything from the plugin.
*/

#ifndef SFIO_H_INCLUDED
#define SFIO_H_INCLUDED

#include <istream>
#include <ostream>

#include "sfio_config.h"

namespace SFIO_NAMESPACE {
namespace sfio {

// This engine's input. Every command it will ever execute is read from here.
std::istream& in();

// This engine's output. Every "info", "bestmove" and "id" line goes here:
// sync_cout expands to this stream, so the macro alone covers nearly all of it.
std::ostream& out();

// Points the streams at `read_fd` and `write_fd`.
//
// Discards anything a previous engine left buffered on either side, so that a
// restart neither emits the tail of a dead engine's output nor executes commands
// queued for it, and clears the streams' error state so that it does not inherit
// a previous end-of-file.
//
// Returns false if either descriptor is not open, in which case the streams stay
// bound to whatever they were using before.
bool bind(int read_fd, int write_fd);

}  // namespace sfio
}  // namespace SFIO_NAMESPACE

#endif  // #ifndef SFIO_H_INCLUDED
