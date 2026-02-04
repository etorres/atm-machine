package es.eriktorr
package atm.domain

import cash.domain.{Denomination, Quantity}

trait PhysicalDispenser[F[_]]:
  def dispense(
      notes: Map[Denomination, Quantity],
  ): F[Unit]
