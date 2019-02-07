# google directory lambda authorizer
This project enables Google Groups-controlled access to API Gateway endpoints.  You can create a [API Gateway custom authorizer](https://docs.aws.amazon.com/apigateway/latest/developerguide/apigateway-use-lambda-authorizer.html) that reads a user's email address from an OAuth token and makes a call to the [Directory API](https://developers.google.com/admin-sdk/directory/) to check that the user is a member of a set of predefined groups. 

An example use case would be a client-side web app using [Google Sign-In for Websites](https://developers.google.com/identity/sign-in/web/) backed by API Gateway. With the custom authorizer provided by this repository, the client can pass the ID token generated by Google Sign-In to the authorizer function, granting access to the API if group membership requirements are satisfied. 

More information about the way the authorizer receives Google ID can be found [here](https://developers.google.com/identity/sign-in/web/backend-auth).

## requirements
There are a few things to set up in order for all of this to work. You'll need: 
### 1. a Google Cloud project
* enable the [Admin SDK API](https://console.cloud.google.com/apis/library/admin.googleapis.com) 
* create a [service account](https://console.cloud.google.com/iam-admin/serviceaccounts)
* generate a P12 key  from the above service account and upload it to S3
* add the `admin.directory.group.readonly` scope to the service account (you'll need to ask a G Suite administrator/IT support)

### 2. a lambda to use as the custom authorizer
* create an object that wraps an instance of `GoogleDirectoryLambdaAuthorizer`...
```scala
object MyCustomAuthorizer extends RequestStreamHandler {
  def initAuthorizer(): Either[Throwable, GoogleDirectoryLambdaAuthorizer] =
    GoogleDirectoryLambdaAuthorizer.forServiceAccount(
      applicationName = "My great application",
      requiredGroups = Set("powerusers@guardian.co.uk", "security@guardian.co.uk"),
      oauthClientId = ClientId("abc123"),
      s3Client = amazonS3Client,
      p12KeyPath = S3Path("private-bucket", "credentials.p12"),
      serviceAccountId = EmailAddress("service@myapp.iam.gserviceaccount.com"),
      userToImpersonate = EmailAddress("admin@guardian.co.uk")
    )

  override def handleRequest(input: InputStream, output: OutputStream, context: Context): Unit =
    initAuthorizer().fold(
      err => throw err,
      _.map(_.handleRequest(input, output, context))
    )
}
``` 
...and create a lambda that uses this object for its handler. Note the `S3Path` must contain the key uploaded from step 1, with read access for the lambda.

### 3. an API Gateway API configured to use the authorizer
Create an authorizer using the function from 2. in your API. For example, with the CLI:
```bash
aws apigateway create-authorizer --rest-api-id 1234123412 \
    --name 'GoogleDirectoryAuthorizer' \
    --type TOKEN \
    --authorizer-uri 'arn:aws:apigateway:eu-west-1:lambda:path/2015-03-31/functions/arn:aws:lambda:eu-west-1:123412341234:function:customAuthFunction/invocations' \
    --identity-source 'method.request.header.Authorization' \
    --identity-validation-expression 'Bearer (.*)' \
    --authorizer-result-ttl-in-seconds 0
```

Note that you must disable caching (TTL 0) since the method ARN is included in the policy returned by the authorizer.

You can now send requests to your API including an `Authorization` header in the requests:
`Authorization: Bearer YOUR_TOKEN_HERE`