package com.gu.directoryauthorizer

import cats.syntax.either._
import com.google.api.client.googleapis.auth.oauth2.{GoogleIdToken, GoogleIdTokenVerifier}
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.jackson2.JacksonFactory
import com.gu.directoryauthorizer.ServiceAccountGoogleDirectoryAPIClient.EmailAddress
import com.gu.directoryauthorizer.GoogleOAuthTokenReader.{ClientId, IdToken}

import scala.concurrent.ExecutionContext

trait GoogleOAuthTokenReader[F[_]] {

  def emailAddress(token: IdToken): F[EmailAddress]
}

class DefaultGoogleOAuthTokenReader(clientId: ClientId)(implicit ec: ExecutionContext)
    extends GoogleOAuthTokenReader[Either[Throwable, ?]] {

  import scala.collection.JavaConverters._

  val httpTransport = new NetHttpTransport()
  val jsonFactory = new JacksonFactory()

  private val verifier: GoogleIdTokenVerifier =
    new GoogleIdTokenVerifier.Builder(httpTransport, jsonFactory)
      .setAudience(List(clientId.value).asJava)
      .build()

  private def verifyToken(token: IdToken): Either[Throwable, GoogleIdToken] =
    Either.catchNonFatal(verifier.verify(token.value)).flatMap { tokenOrNull =>
      Either.fromOption(
        Option(tokenOrNull),
        new Exception(
          s"Failed to verify ID token ${token.value} (Google verification returned null). Client ID ${clientId.value}"))
    }

  def emailAddress(token: IdToken): Either[Throwable, EmailAddress] =
    verifyToken(token).map(token => EmailAddress(token.getPayload.getEmail))
}

object GoogleOAuthTokenReader {

  // ID token of the user to authenticate
  case class IdToken(value: String) extends AnyVal

  // CLIENT_ID of the OAuth 2.0 application (under "OAuth 2.0 client IDs" in https://console.cloud.google.com/apis/credentials)
  case class ClientId(value: String) extends AnyVal

}
