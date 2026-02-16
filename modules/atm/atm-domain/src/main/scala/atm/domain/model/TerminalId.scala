package es.eriktorr
package atm.domain.model

import cash.domain.model.types.NonEmptyString

import io.github.iltotore.iron.RefinedSubtype

type TerminalId = TerminalId.T

object TerminalId extends RefinedSubtype[String, NonEmptyString]
