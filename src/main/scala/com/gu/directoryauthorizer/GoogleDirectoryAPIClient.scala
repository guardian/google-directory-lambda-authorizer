package com.gu.directoryauthorizer

import java.io.File
import java.nio.file.{Files, StandardCopyOption}

import cats.Monad
import cats.implicits._
import com.amazonaws.services.s3.AmazonS3
import com.google.api.client.googleapis.auth.oauth2.GoogleCredential
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport
import com.google.api.client.json.jackson2.JacksonFactory
import com.google.api.services.admin.directory.{Directory, DirectoryScopes}
import com.gu.directoryauthorizer.ServiceAccountGoogleDirectoryAPIClient.{GroupKey, MemberKey}

import scala.collection.JavaConverters._
import scala.concurrent.{ExecutionContext, Future}

trait GoogleDirectoryAPIClient[F[_]] {

  def hasMember(groupKey: GroupKey, memberKey: MemberKey): F[Boolean]

  def memberOfAll(groups: List[GroupKey], member: MemberKey)(implicit F: Monad[F]): F[Boolean] =
    groups.traverse(group => hasMember(group, member)).map(_.forall(identity))
}

// Directory API client implementation using service account credentials. For this to work, you need:
//  1. a Google Cloud project with the Admin SDK API enabled
//  2. a service account created in the above project (no project access roles are required)
//  3. a P12 key generated from the above service account, available as a `File` in the config
//  4. `admin.directory.group.readonly` scope access for the service account (a G Suite administrator can do this)
class ServiceAccountGoogleDirectoryAPIClient(config: ServiceAccountGoogleDirectoryAPIClient.Config)(
    implicit ec: ExecutionContext)
    extends GoogleDirectoryAPIClient[Future] {

  // we only use hasMember, read-only access is enough
  private val scopes = List(DirectoryScopes.ADMIN_DIRECTORY_GROUP_READONLY)

  private val transport = GoogleNetHttpTransport.newTrustedTransport()
  private val jsonFactory = JacksonFactory.getDefaultInstance

  private val credential = new GoogleCredential.Builder()
    .setTransport(transport)
    .setJsonFactory(jsonFactory)
    .setServiceAccountId(config.serviceAccountId.value)
    .setServiceAccountPrivateKeyFromP12File(config.p12Key)
    .setServiceAccountScopes(scopes.asJava)
    .setServiceAccountUser(config.userToImpersonate.value)
    .build()

  private val client: Directory =
    new Directory.Builder(transport, jsonFactory, credential)
      .setApplicationName(config.applicationName)
      .build()

  def hasMember(groupKey: GroupKey, memberKey: MemberKey): Future[Boolean] =
    Future(
      client
        .members()
        .hasMember(groupKey.value, memberKey.value)
        .execute()
        .getIsMember)
}

object ServiceAccountGoogleDirectoryAPIClient {

  // the group's email address, group alias, or the unique group ID
  case class GroupKey(value: String) extends AnyVal

  // the user's primary email address, alias, or unique ID
  case class MemberKey(value: String) extends AnyVal

  case class EmailAddress(value: String) extends AnyVal

  case class S3Path(bucket: String, path: String)

  case class Config(
      applicationName: String,
      p12Key: File,
      serviceAccountId: EmailAddress, // the email address shown in the Cloud console after creating the service account
      userToImpersonate: EmailAddress // the email address of a user with admin rights for your G Suite domain
  )

  // Instantiate a service account-authenticated Directory client where the service account's P12 key is stored in S3.
  def fromP12KeyInS3(s3Client: AmazonS3,
                     p12KeyPath: S3Path,
                     applicationName: String,
                     serviceAccountId: EmailAddress,
                     userToImpersonate: EmailAddress)(
      implicit ec: ExecutionContext): Either[Throwable, ServiceAccountGoogleDirectoryAPIClient] =
    for {
      key <- Either.catchNonFatal(s3Client.getObject(p12KeyPath.bucket, p12KeyPath.path))
      keyFile <- Either.catchNonFatal(File.createTempFile("service_account_credentials", ".tmp"))
      _ <- Either.catchNonFatal(Files.copy(key.getObjectContent, keyFile.toPath, StandardCopyOption.REPLACE_EXISTING))
      conf = Config(applicationName, keyFile, serviceAccountId, userToImpersonate)
    } yield new ServiceAccountGoogleDirectoryAPIClient(conf)

}
