package com.gu.directoryauthorizer

import java.io.{InputStream, OutputStream}

import cats.data.EitherT
import cats.instances.future._
import cats.syntax.applicativeError._
import com.amazonaws.services.lambda.runtime.{Context, RequestStreamHandler}
import com.amazonaws.services.s3.AmazonS3
import com.gu.directoryauthorizer.GoogleDirectoryLambdaAuthorizer.TokenInput
import com.gu.directoryauthorizer.GoogleOAuthTokenReader.{ClientId, IdToken}
import com.gu.directoryauthorizer.ServiceAccountGoogleDirectoryAPIClient.{EmailAddress, GroupKey, MemberKey, S3Path}
import io.circe.generic.auto._
import io.circe.parser._
import io.circe.syntax._

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.io.Source

class GoogleDirectoryLambdaAuthorizer(tokenReader: DefaultGoogleOAuthTokenReader,
                                      directoryClient: ServiceAccountGoogleDirectoryAPIClient,
                                      requiredMembership: Set[GroupKey])(implicit ec: ExecutionContext)
    extends RequestStreamHandler {

  private[directoryauthorizer] def authorize(input: TokenInput): EitherT[Future, Throwable, AuthResponse] =
    for {
      token <- EitherT.fromEither(input.parsedToken)
      email <- EitherT.fromEither(tokenReader.emailAddress(token))
      allowed <- directoryClient.memberOfAll(requiredMembership.toList, MemberKey(email.value)).attemptT
    } yield {
      if (allowed) AuthResponse.allowMethodInvoke(email.value, input.methodArn)
      else AuthResponse.denyAll(email.value)
    }

  override def handleRequest(input: InputStream, output: OutputStream, context: Context): Unit = {

    val body = Source.fromInputStream(input).mkString

    val authResponse = Await.result(EitherT
                                      .fromEither(decode[TokenInput](body))
                                      .flatMap(authorize)
                                      .valueOr(_ => AuthResponse.denyAll("unknown user")),
                                    60.seconds)

    output.write(authResponse.asJson.noSpaces.getBytes)
    output.close()
  }
}

object GoogleDirectoryLambdaAuthorizer {

  def forServiceAccount(
      applicationName: String,
      requiredGroups: Set[GroupKey], // member must be in all of these groups to execute the function
      oauthClientId: ClientId, // client ID for the application you use to request OAuth consent (not the service account ID)
      s3Client: AmazonS3,
      p12KeyPath: S3Path, // S3 location of the service account's P12 key
      serviceAccountId: EmailAddress, // the email address shown in the Cloud console after creating the service account
      userToImpersonate: EmailAddress) // the email address of a user with admin rights for your G Suite domain
  (implicit ec: ExecutionContext): Either[Throwable, GoogleDirectoryLambdaAuthorizer] =
    ServiceAccountGoogleDirectoryAPIClient
      .fromP12KeyInS3(s3Client, p12KeyPath, applicationName, serviceAccountId, userToImpersonate)
      .map { directoryClient =>
        new GoogleDirectoryLambdaAuthorizer(new DefaultGoogleOAuthTokenReader(oauthClientId),
                                            directoryClient,
                                            requiredGroups)
      }

  // Input for a lambda authorizer of the `TOKEN` type.
  // https://docs.aws.amazon.com/apigateway/latest/developerguide/api-gateway-lambda-authorizer-input.html
  case class TokenInput(`type`: String, authorizationToken: String, methodArn: String) {

    val parsedToken: Either[Throwable, IdToken] = authorizationToken match {
      case TokenInput.regex(parsed) => Right(IdToken(parsed))
      case _ =>
        Left(new Exception(s"Token $authorizationToken did not match regex ${TokenInput.regex}"))
    }
  }

  object TokenInput {
    private val regex = "Bearer (.*)".r
  }
}
