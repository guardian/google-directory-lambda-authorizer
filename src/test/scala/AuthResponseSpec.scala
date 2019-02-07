package com.gu.erasure.lambda

import com.amazonaws.auth.policy.{Policy, Statement}
import com.gu.directoryauthorizer.{AuthResponse, AuthResponseJson}
import org.scalactic.{Equality, _}
import org.scalatest.{EitherValues, FreeSpec, Matchers}

class AuthResponseSpec
    extends FreeSpec
    with Matchers
    with EitherValues
    with TripleEqualsSupport
    with AWSPolicyEquality {

  import io.circe.generic.auto._
  import io.circe.parser._

  implicit val authResponseEq: Equality[AuthResponse] = new Equality[AuthResponse] {
    override def areEqual(a: AuthResponse, b: Any): Boolean = b match {
      case b: AuthResponse =>
        a.principalId == b.principalId && a.policyDocument === b.policyDocument
      case _ => false
    }
  }

  "`AuthResponse`" - {
    "generates valid API Gateway Lambda Authorizer output for invoke permissions" in {
      val user = "user"
      val resource = "resource"

      // https://docs.aws.amazon.com/apigateway/latest/developerguide/api-gateway-lambda-authorizer-output.html
      val expected =
        s"""{
          "principalId": "$user",
          "policyDocument": {
            "Version": "2012-10-17",
            "Statement": [
              {
                "Action": "execute-api:Invoke",
                "Effect": "Allow",
                "Resource": "$resource"
              }
            ]
          }
        }"""

      val decoded = decode[AuthResponseJson](expected).flatMap(_.toAuthResponse)

      decoded should be('right)
      assert(AuthResponse.allowMethodInvoke(user, resource) === decoded.right.get)
    }
    "generates valid API Gateway Lambda Authorizer output for deny permissions" in {
      val user = "user"

      val expected =
        s"""{
          "principalId": "$user",
          "policyDocument": {
            "Version": "2012-10-17",
            "Statement": [
              {
                "Action": "*",
                "Effect": "Deny",
                "Resource": "*"
              }
            ]
          }
        }"""

      val decoded = decode[AuthResponseJson](expected).flatMap(_.toAuthResponse)

      decoded should be('right)
      assert(AuthResponse.denyAll(user) === decoded.right.get)
    }
  }
}

// Unfortunately this is required as there's no equals defined for Policy in the SDK.
trait AWSPolicyEquality {

  import scala.collection.JavaConverters._

  private def collectionEquality[A](as: Iterable[A], bs: Iterable[A])(equal: (A, A) => Boolean): Boolean =
    as.size == bs.size && as.zip(bs).forall(equal.tupled)

  implicit val statementEquality: Equality[Statement] = new Equality[Statement] {
    override def areEqual(a: Statement, b: Any): Boolean = b match {
      case b: Statement =>
        a.getId == b.getId &&
          a.getPrincipals == b.getPrincipals &&
          a.getEffect == b.getEffect &&
          a.getConditions == b.getConditions &&
          collectionEquality(a.getActions.asScala, b.getActions.asScala)(_.getActionName == _.getActionName) &&
          collectionEquality(a.getResources.asScala, b.getResources.asScala)(_.getId == _.getId)

      case _ => false
    }
  }

  implicit val policyEq: Equality[Policy] = new Equality[Policy] {
    override def areEqual(a: Policy, b: Any): Boolean = b match {
      case b: Policy =>
        a.getVersion == b.getVersion &&
          a.getId == b.getId &&
          collectionEquality(a.getStatements.asScala, b.getStatements.asScala)(statementEquality.areEqual)

      case _ => false
    }
  }
}
