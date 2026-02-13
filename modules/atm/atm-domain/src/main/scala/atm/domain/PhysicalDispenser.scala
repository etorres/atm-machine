package es.eriktorr
package atm.domain

import cash.domain.model.{Denomination, Quantity}

trait PhysicalDispenser[F[_]]:
  def dispense(
      notes: Map[Denomination, Quantity],
  ): F[Unit]
