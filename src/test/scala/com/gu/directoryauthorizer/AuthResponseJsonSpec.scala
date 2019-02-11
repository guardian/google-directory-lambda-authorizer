package com.gu.directoryauthorizer

import com.amazonaws.auth.policy.Policy
import com.amazonaws.auth.policy.Statement.Effect
import com.gu.directoryauthorizer.AuthResponse.Action
import io.circe.Decoder
import org.scalactic._
import org.scalatest.{EitherValues, FreeSpec, Matchers}

class AuthResponseJsonSpec extends FreeSpec with Matchers with EitherValues with TripleEqualsSupport with Stubs {

  import io.circe.parser._
  import io.circe.generic.auto._
  import scala.collection.JavaConverters._

  implicit def policyDecoder: Decoder[Policy] =
    Decoder.decodeJson.map(json => Policy.fromJson(json.toString))

  "AuthResponse.json" - {
    val principal = "foo@bar.com"
    val arn = "ARN"
    val allow = AuthResponse.allowMethodInvoke(principal, arn)
    val deny = AuthResponse.denyAll(principal)

    "allow permissions" - {

      val decodedAllow = decode[AuthResponse](allow.json)
      "produces valid JSON" in {
        decodedAllow should be('right)
      }

      "assigns correct values" - {
        "principalId" in {
          decodedAllow.right.value.principalId should be(principal)
        }

        "policyDocument" - {
          val statements = decodedAllow.right.value.statements

          "action" in {
            assert(statements.forall(_.getActions.asScala.forall(_.getActionName == Action.invoke.getActionName)))
          }

          "effect" in {
            assert(statements.forall(_.getEffect == Effect.Allow))
          }

          "resource" in {
            assert(statements.forall(_.getResources.asScala.forall(_.getId == arn)))
          }
        }
      }
    }

    "deny permissions" - {
      val decodedDeny = decode[AuthResponse](deny.json)
      "produces valid JSON" in {
        decodedDeny should be('right)
      }

      "assigns correct values" - {
        "principalId" in {
          decodedDeny.right.value.principalId should be(principal)
        }

        "policyDocument" - {
          val statements = decodedDeny.right.value.statements

          "action" in {
            assert(statements.forall(_.getActions.asScala.forall(_.getActionName == "*")))
          }

          "effect" in {
            assert(statements.forall(_.getEffect == Effect.Deny))
          }

          "resource" in {
            assert(statements.forall(_.getResources.asScala.forall(_.getId == "*")))
          }
        }
      }
    }
  }
}
