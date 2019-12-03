package com.ukonnra.chessur

import cats._
import cats.effect.{Async, ExitCode, IO, IOApp}
import cats.implicits._
import com.ukonnra.chessur.admin.AdminClient
import com.ukonnra.chessur.definitions.{acceptLoginRequest, completedRequest, loginRequest, oAuth2Client}
import com.ukonnra.chessur.public.PublicClient
import org.http4s.client.Client
import org.http4s.client.blaze.BlazeClientBuilder
import org.http4s.dsl.impl.Root
import org.http4s.server.Router
import cats.implicits._
import io.circe.Json
import org.http4s.HttpRoutes
import org.http4s.server.blaze._
import org.http4s.dsl.io._
import org.http4s.circe._

import scala.concurrent.ExecutionContext.Implicits.global
import org.http4s.server.Router
import org.http4s.implicits._
import io.circe.generic.auto._

case class UserService[F[_]: Monad: Async](
    adminClient: AdminClient[F],
    publicClient: PublicClient[F]
) {
  def login(username: String, password: String): F[Unit] = {
    for {
      resp <- adminClient.getConsentRequest(
        s"username: ${username}; passowrd: $password"
      )
      _ = resp.fold(println, println, println, println)
    } yield ()
  }

  def getWellKnown: F[Unit] = {
    for {
      resp <- publicClient.wellKnown()
      _ = resp.fold(println, println)
    } yield ()
  }

  def createClient: F[Unit] = {
    for {
      resp <- adminClient.createOAuth2Client(
        oAuth2Client(
          clientId = Some("new-client"),
          clientSecret = Some("secret"),
          grantTypes = Some(IndexedSeq("authorization_code", "refresh_token")),
          responseTypes = Some(IndexedSeq("code", "id_token")),
          scope = Some("openid,offline"),
          redirectUris = Some(IndexedSeq("http://www.google.com"))
        )
      )
      _ = resp.fold(println, println, println, println)
    } yield ()
  }
}

final case class User(username: String, password: String, challenge: String)

object Main extends IOApp {
  private implicit val userDecode = jsonOf[IO, User]
  object LoginChallengeQueryParamMatcher extends QueryParamDecoderMatcher[String]("login_challenge")

  override def run(args: List[String]): IO[ExitCode] = {
    BlazeClientBuilder[IO](global).resource.use { client =>
      implicit val implicitClient: Client[IO] = client
      val userService = UserService[IO](
        AdminClient[IO]("http://localhost:4445"),
        PublicClient[IO]("http://localhost:4444")
      )
      val service = HttpRoutes.of[IO] {
        case POST -> Root / "clients" =>
          for {
            _ <- userService.createClient
            resp <- Ok(())
          } yield resp
        case req @ POST -> Root =>
          for {
            user <- req.as[User]
            resp <- userService.adminClient.acceptLoginRequest(user.challenge, body = Some(acceptLoginRequest(
              subject = user.username, remember = Some(true), rememberFor = Some(3600L), acr = Some("0")
            )))
            data = resp.fold(Some.apply, err => {println(err); None}, err => {println(err); None}, err => {println(err); None} )
            _ = println(s"data: $data")
            resp <- data match {
              case Some(completedRequest(Some(redirectTo))) => TemporaryRedirect(redirectTo)
              case _ => BadRequest()
            }
          } yield resp

        case GET -> Root :? LoginChallengeQueryParamMatcher(challenge) =>
          for {
            resp <- userService.adminClient.getLoginRequest(challenge)
            data = resp.fold(Some.apply, err => {println(err); None}, err => {println(err); None}, err => {println(err); None} , err => {println(err); None})
            resp <- Ok(data.toString)
          } yield resp
      }
      val httpApp = Router("/" -> service).orNotFound
      val serverBuilder =
        BlazeServerBuilder[IO].bindHttp(8080, "localhost").withHttpApp(httpApp)
      serverBuilder.resource.use(_ => IO.never)
    }
  }.map(_ => ExitCode.Success)
}
