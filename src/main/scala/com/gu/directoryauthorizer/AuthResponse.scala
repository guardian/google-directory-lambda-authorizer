package com.gu.directoryauthorizer

import cats.syntax.either._
import com.amazonaws.auth.policy.Statement.Effect
import com.amazonaws.auth.policy.{Policy, Resource, Statement, Action => AWSAction}
import io.circe.{Encoder, Json}
import io.circe.parser._

// response from a custom API Gateway authorizer
// https://docs.aws.amazon.com/apigateway/latest/developerguide/api-gateway-lambda-authorizer-output.html
case class AuthResponse(principalId: String, policyDocument: Policy)

object AuthResponse {

  implicit val authResponseEncoder: Encoder[AuthResponse] =
    Encoder.instance(
      res =>
        Json.obj(
          "principalId" -> Json.fromString(res.principalId),
          "policyDocument" -> AuthResponseJson.fromAuthResponse(res).policyDocument
      ))

  case class Action(name: String) extends AWSAction {
    override def getActionName: String = name
  }

  def allowMethodInvoke(principalId: String, methodArn: String): AuthResponse = {
    val statement = new Statement(Effect.Allow)
      .withActions(Action("execute-api:Invoke"))
      .withResources(new Resource(methodArn))

    AuthResponse(principalId, new Policy().withStatements(statement))
  }

  def denyAll(principalId: String): AuthResponse = {
    val statement = new Statement(Effect.Deny)
      .withActions(Action("*"))
      .withResources(new Resource("*"))

    AuthResponse(principalId, new Policy().withStatements(statement))
  }
}

case class AuthResponseJson(principalId: String, policyDocument: Json) {

  def toAuthResponse: Either[Throwable, AuthResponse] =
    Either
      .catchNonFatal(Policy.fromJson(policyDocument.toString))
      .map(policy => AuthResponse(principalId, policy))
}

object AuthResponseJson {

  import io.circe.parser._

  def fromAuthResponse(authResponse: AuthResponse): AuthResponseJson =
    parse(authResponse.policyDocument.toJson)
      .map(json => AuthResponseJson(authResponse.principalId, json))
      .right
      .get

}
