package com.gu.directoryauthorizer

import java.io.File

import com.amazonaws.auth.policy.Statement
import com.gu.directoryauthorizer.ServiceAccountDirectoryAPIClient.{Config, GroupKey, MemberKey}

import scala.collection.JavaConverters._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

trait Stubs {

  val emptyConfig = Config("unused", File.createTempFile("unused", "unused"), "unused", "unused")

  def stubDirectoryAPIClient(membership: Map[GroupKey, List[MemberKey]]): GoogleDirectoryAPIClient =
    (groupKey, memberKey) => Future.successful(membership.get(groupKey).exists(_.contains(memberKey)))

  def stubTokenReader: GoogleOAuthTokenReader =
    token => Right(token.value)

  def stubAuthorizer(membership: Map[GroupKey, List[MemberKey]],
                     requiredMembership: Set[GroupKey]): GoogleDirectoryLambdaAuthorizer =
    new GoogleDirectoryLambdaAuthorizer(stubTokenReader, stubDirectoryAPIClient(membership), requiredMembership)

  implicit class TestableAuthResponse(authResponse: AuthResponse) {
    val statements: List[Statement] = authResponse.policyDocument.getStatements.asScala.toList
  }
}
