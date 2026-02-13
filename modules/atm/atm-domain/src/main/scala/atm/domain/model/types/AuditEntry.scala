package es.eriktorr
package atm.domain.model.types

import atm.domain.model.AccountId
import cash.domain.model.*

import cats.Show
import cats.derived.*
import org.typelevel.cats.time.instances.instant.given

import java.time.Instant
import java.util.UUID

final case class AuditEntry(
    id: UUID,
    accountId: AccountId,
    money: Money,
    state: TransactionState,
    timestamp: Instant,
) derives Show
