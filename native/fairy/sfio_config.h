/*
  The C++ namespace this package's engine is compiled into.

  sfio.h and sfio.cpp are byte-identical across the three native packages; this
  file is the only thing that differs between them. Each flavour therefore gets
  its own pair of streams, which is what lets two of them be resident in one
  process without sharing a channel.
*/

#ifndef SFIO_CONFIG_H_INCLUDED
#define SFIO_CONFIG_H_INCLUDED

#define SFIO_NAMESPACE FairyStockfish

#endif  // #ifndef SFIO_CONFIG_H_INCLUDED
