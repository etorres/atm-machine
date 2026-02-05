package es.eriktorr
package atm.infrastructure

import cats.effect.{IO, IOApp}
import io.github.timwspence.cats.stm.STM

@SuppressWarnings(Array("org.wartremover.warts.Any"))
object STMExample extends IOApp.Simple:
  val run: IO[Unit] =
    // 1. Create the STM runtime using the companion object method
    STM.runtime[IO].flatMap { stm =>
      // Import the algebra from the runtime instance to access
      // types like TVar and methods like retry
      import stm.*

      for
        // 2. Create TVars within a transaction
        // TVar.of(value) returns a Txn[TVar[A]], so we commit it to get the variable.
        (accountA, accountB) <- stm.commit(for
          a <- TVar.of(100)
          b <- TVar.of(0)
        yield (a, b))

        // 3. Define a transaction to modify the variables atomically
        // This moves 50 units from A to B
        transfer =
          for
            balance <- accountA.get
            _ <-
              if balance >= 50 then accountA.modify(_ - 50) >> accountB.modify(_ + 50)
              else retry // Retries the transaction if precondition isn't met
          yield ()

        // 4. Execute the transfer transaction
        _ <- stm.commit(transfer)

        // 5. Read the final state atomically
        result <- stm.commit(for
          a <- accountA.get
          b <- accountB.get
        yield (a, b))

        _ <- IO.println(s"Final State -> Account A: ${result._1}, Account B: ${result._2}")
      yield ()
    }
