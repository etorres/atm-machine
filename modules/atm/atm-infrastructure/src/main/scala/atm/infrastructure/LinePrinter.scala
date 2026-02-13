package es.eriktorr
package atm.infrastructure

import cats.effect.std.Console

trait LinePrinter[F[_]]:
  def print(line: String): F[Unit]

object LinePrinter:
  def apply[F[_]: Console]: LinePrinter[F] =
    (line: String) => Console[F].println(line)
