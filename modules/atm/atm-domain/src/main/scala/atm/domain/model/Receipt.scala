package es.eriktorr
package atm.domain.model

import atm.domain.model.types.TransactionId
import cash.domain.model.Money

import cats.derived.*
import cats.Show

import java.time.OffsetDateTime

final case class Receipt(
    transactionId: TransactionId,
    terminalId: TerminalId,
    accountId: AccountId,
    money: Money,
    timestamp: OffsetDateTime,
    operationType: Receipt.OperationType,
)

object Receipt:
  enum OperationType derives Show:
    case Withdrawal
