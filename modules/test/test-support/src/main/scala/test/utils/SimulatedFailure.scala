package es.eriktorr
package test.utils

import scala.util.control.NoStackTrace

object SimulatedFailure extends RuntimeException("Simulated failure") with NoStackTrace
