package com.corem.part3concurrency

import com.corem.part3concurrency.AsynchronousEffects.LoginService.{AuthError, UserProfile}
import zio.*
import com.corem.utils.*

import java.util.concurrent.{ExecutorService, Executors}
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

object AsynchronousEffects extends ZIOAppDefault {

  // Callback based
  // Asynchronous
  object LoginService {
    case class AuthError(message: String)
    case class UserProfile(email: String, name: String)

    // Thread pool
    val executor = Executors.newFixedThreadPool(8)

    // "Database"
    val passwd = Map(
      "daniel@rockthejvm.com" -> "RochTheJVM1!"
    )

    // "Profile data"
    val database = Map(
      "daniel@rockthejvm.com" -> "Daniel"
    )

    def login(
        email: String,
        password: String
    )(onSuccess: UserProfile => Unit, onFailure: AuthError => Unit) =
      executor.execute { () =>
        println(
          s"[${Thread.currentThread().getName}] Attempting login for $email"
        )
        passwd.get(email) match {
          case Some(`password`) =>
            onSuccess(UserProfile(email, database(email)))
          case Some(_) => onFailure(AuthError("Incorrect password"))
          case None =>
            onFailure(AuthError(s"User $email doesn't exist. Please sign up."))
        }
      }
  }

  def loginAsZIO(
      id: String,
      pw: String
  ): ZIO[Any, LoginService.AuthError, LoginService.UserProfile] =
    ZIO.async[Any, LoginService.AuthError, LoginService.UserProfile] {
      cb => // callback object created by ZIO
        LoginService.login(id, pw)(
          profile => cb(ZIO.succeed(profile)),
          error => cb(ZIO.fail(error))
        )
    }

  val loginProgram = for {
    email <- Console.readLine("Email: ")
    pass <- Console.readLine("Password: ")
    profile <- loginAsZIO(email, pass).debugThread
    _ <- Console
      .printLine(s"Welcome to Rock the JVM, ${profile.name}")
  } yield ()

  /** Exercices
    */

  // 1. Surface a computation running on some (external) thread to a ZIO
  def external2ZIO[A](computation: () => A)(executor: ExecutorService): Task[A] =
    ZIO.async[Any, Throwable, A] { cb =>
      executor.execute { () =>
        try {
          val result = computation()
          cb(ZIO.succeed(result))
        } catch {
          case e: Throwable => cb(ZIO.fail(e))
        }
      }
    }

  val demoExternal2ZIO = {
    val executor = Executors.newFixedThreadPool(8)
    val zio = external2ZIO { () =>
      println(s"[${Thread.currentThread().getName}] computing the meaning of life on some thread")
      Thread.sleep(1000)
      42
    } (executor)

    zio.debugThread.unit
  }

  // 2. Lift a future to a ZIO
  def future2ZIO[A](future: => Future[A])(using ec: ExecutionContext): Task[A] =
    ZIO.async[Any, Throwable, A] { cb =>
      future.onComplete {
        case Success(value) => cb(ZIO.succeed(value))
        case Failure(ex) => cb(ZIO.fail(ex))
      }
    }

  lazy val demoFuture2ZIO = {
    val executor = Executors.newFixedThreadPool(8)
    implicit val ec = ExecutionContext.fromExecutorService(executor)
    val mol: Task[Int] = future2ZIO(Future {
      println(s"[${Thread.currentThread().getName}] computing the meaning of life on some thread")
      Thread.sleep(1000)
      42
    })

    mol.debugThread.unit
  }

  // 3. Implement a never ending ZIO
  def neverEndingZIO[A]: UIO[A] =
    ZIO.async(_ => ())

  val never = ZIO.never

  def run = ZIO.succeed("Computing ...").debugThread *> neverEndingZIO[Int] *> ZIO.succeed("Completed !").debugThread
}
