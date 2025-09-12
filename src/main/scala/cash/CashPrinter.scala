package es.eriktorr
package cash

import cats.effect.std.Console

trait CashPrinter[F[_]]:
  def print(line: String): F[Unit]

object CashPrinter:
  def impl[F[_]: Console]: CashPrinter[F] =
    (line: String) => Console[F].println(line)
