package es.eriktorr
package atm.domain.model.types

import cats.Show
import cats.derived.*

enum TransactionState derives Show:
  case Started, Debited, Completed, Refunding, Refunded, ManualInterventionRequired
