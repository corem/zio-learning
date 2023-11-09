package com.corem.part3concurrency

import zio._
import com.corem.utils._

object Schedules extends ZIOAppDefault {

  val aZIO = Random.nextBoolean.flatMap { flag =>
    if (flag) ZIO.succeed("Fetched value!").debugThread
    else ZIO.succeed("Failure...").debugThread *> ZIO.fail("Error")
  }

  val aRetriedZIO = aZIO.retry(Schedule.recurs(10)) // retries 10 times, returns the first success, last failure

  // Schedules are data structures that describe how effects should be timed
  val oneTimeSchedule = Schedule.once
  val recurrentSchedule = Schedule.recurs(10)
  val fixedIntervalSchedule = Schedule.spaced(1.second)

  // Exponential backoff
  val exBackoffSchedule = Schedule.exponential(1.second, 2.0)
  val fiboSchedule = Schedule.fibonacci(1.second) // 1s, 1s, 2s, 3s, 5s, 8s, ...

  // Combinators
  val recurrentAndSpaced = Schedule.recurs(3) && Schedule.spaced(1.second) // Every attempt is 1 sec apart, 3 attempts total
  // Sequencing
  val recurrentThenSpaced = Schedule.recurs(3) ++ Schedule.spaced(1.second) // 3 retries, then every 1s

  // Schedule have
  // R = environment,
  // I = input (errors in the case of .retry, values in the case of .repeat)
  // O = output (values for the next schedule so that you can do something with them)
  val totalElapsed = Schedule.spaced(1.second) >>> Schedule.elapsed.map(time => println(s"Total time elapsed: $time"))

  def run = aZIO.retry(totalElapsed)
}
