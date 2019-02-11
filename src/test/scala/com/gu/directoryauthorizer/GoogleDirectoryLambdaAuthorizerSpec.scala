package com.gu.directoryauthorizer

import com.amazonaws.auth.policy.Statement.Effect
import com.gu.directoryauthorizer.ServiceAccountDirectoryAPIClient.{GroupKey, MemberKey}
import com.gu.directoryauthorizer.GoogleDirectoryLambdaAuthorizer.TokenInput
import org.scalatest.{AsyncFreeSpec, EitherValues, Matchers}

class GoogleDirectoryLambdaAuthorizerSpec extends AsyncFreeSpec with EitherValues with Matchers with Stubs {

  import scala.collection.JavaConverters._

  "GoogleDirectoryLambdaAuthorizer" - {
    val membership = Map(
      GroupKey("1") -> List("a", "b", "c").map(MemberKey),
      GroupKey("2") -> List("a", "d", "e", "f").map(MemberKey),
      GroupKey("3") -> List("a").map(MemberKey),
      GroupKey("4") -> List("f").map(MemberKey)
    )
    val requiredMembership = Set("2", "4").map(GroupKey)
    val arn = "SOME_ARN"

    val authorizer = stubAuthorizer(membership, requiredMembership)

    "grants access for users in the required groups" in {
      val userId = "f"

      authorizer.authorize(token(userId, arn)).value.map { result =>
        val statements = result.right.value.statements

        result.right.value.principalId should be(userId)
        assert(statements.forall(_.getEffect == Effect.Allow))
        assert(statements.forall(_.getResources.asScala.forall(_.getId == arn)))
      }
    }

    "denies access for users not in required groups" in {
      val userId = "a"

      authorizer.authorize(token(userId, arn)).value.map { result =>
        val statements = result.right.value.statements

        result.right.value.principalId should be(userId)
        assert(statements.forall(_.getEffect == Effect.Deny))
      }
    }
  }

  def token(userId: String, arn: String): TokenInput = TokenInput("unused", s"Bearer $userId", arn)
}
