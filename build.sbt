name := "google-directory-lambda-authorizer"

version := "0.0.1"

scalaVersion := "2.12.8"

val circeVersion = "0.10.0"

libraryDependencies ++= Seq(
  "com.amazonaws" % "aws-java-sdk-s3" % "1.11.491",
  "com.amazonaws" % "aws-lambda-java-core" % "1.2.0",
  "com.amazonaws" % "aws-lambda-java-events" % "1.2.0",
  "com.google.apis" % "google-api-services-oauth2" % "v2-rev137-1.23.0",
  "com.google.apis" % "google-api-services-admin-directory" % "directory_v1-rev105-1.25.0",
  "org.typelevel" %% "cats-core" % "1.6.0",
  "org.scalatest" %% "scalatest" % "3.0.5" % Test
)

libraryDependencies ++= Seq(
  "io.circe" %% "circe-core",
  "io.circe" %% "circe-generic",
  "io.circe" %% "circe-parser"
).map(_ % circeVersion)

addCompilerPlugin("org.spire-math" %% "kind-projector" % "0.9.8")
