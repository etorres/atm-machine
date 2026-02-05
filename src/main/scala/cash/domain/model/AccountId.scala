package es.eriktorr
package cash.domain.model

import cash.domain.model.types.NonEmptyString

import io.github.iltotore.iron.RefinedSubtype

type AccountId = AccountId.T

object AccountId extends RefinedSubtype[String, NonEmptyString]
