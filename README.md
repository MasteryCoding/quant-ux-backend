[![Maven Package](https://github.com/KlausSchaefers/qux-java/actions/workflows/maven-publish.yml/badge.svg)](https://github.com/KlausSchaefers/qux-java/actions/workflows/maven-publish.yml)

[![Docker Image Build and Push to Dockerhub - CI/CD](https://github.com/KlausSchaefers/qux-java/actions/workflows/docker.yml/badge.svg)](https://github.com/KlausSchaefers/qux-java/actions/workflows/docker.yml)

# Quant-UX

This is the Quant-UX server backend. To run this you need a MongoDB server and a folder where
images are stored.

You can try out a running version here: https://quant-ux.com

## Config

Edit the config file (e.g. matc.conf). Do not forget to update the _jwt.password_ property. For a clustered setup, you need to keep
the passwords the same. If you keep the password blank, a random password is generated at startup

```javascript
{
  "debug" : false,
  "http.port" : 8080, // The server port
  "http.host": "your.server.com", // The domain name of your server. This is important for the mail that will be send. Otherwise links will not work
  "image.folder.user" : "test/user", // folder where user images will be stored
  "image.folder.apps" : "test/apps", // folder where app images will be stored
  "image.size" : 50000000, // max image size for uploads
  "mongo.db_name": "MATC", // mongo DB to use
  "mongo.connection_string": "mongodb://localhost:27017", // connection string, might include password and username
  "auth.service": "", // 'keycloak' or ''
  "user.allowSignUp": true,
  "user.allowedDomains": "*",
  "external.api.url": "https://your-external-api.com" // URL for external API token exchange
}
```

You can also provide the configuration through ENV variables. The following variables are supported, and map
to the JSON definitions.

```

    QUX_HTTP_HOST

    QUX_HTTP_PORT

    QUX_MONGO_DB_NAME

    QUX_MONGO_TABLE_PREFIX

    QUX_MONGO_CONNECTION_STRING

    QUX_JWT_PASSWORD

    QUX_IMAGE_FOLDER_USER

    QUX_IMAGE_FOLDER_APPS

    QUX_AUTH_SERVICE

    QUX_USER_ALLOW_SIGNUP

    QUX_USER_ALLOWED_DOMAINS

    QUX_EXTERNAL_API_URL

```

Please note that we have replaced the old config of nested objects with a straight dot notation.

### External API Token Exchange

The server supports token exchange with external APIs. When configured, users from external systems can exchange their external API tokens for Quant-UX JWT tokens.

**Configuration:**

- `external.api.url`: The base URL of your external API (required)

**Usage:**

1. Configure the external API URL in your config file
2. Users can call `POST /rest/user/token-exchange` with their external API token in the `Authorization` header
3. The server will:
   - Validate the token by calling `GET {external.api.url}/v3/user/me` with the token in the `Authorization` header
   - Create or retrieve the corresponding Quant-UX user (idempotent)
   - Return a Quant-UX JWT token

**Example:**

```bash
curl -X POST https://your-quantux-backend.com/rest/user/token-exchange \
  -H "Authorization: Bearer your-external-api-token"
```

The external API must implement `GET /v3/user/me` that returns a user object with at least `id` and `username` fields.

## Mongo optimization

Start the `mongo` shell and run the following commands to set the correct mongo indexes

```

use MATC
db.app.createIndex({"isPublic":1})
db.app.createIndex({"isDirty":1})

db.event.createIndex({"appID":1, "type":1})
db.event.createIndex({"appID":1})

db.mouse.createIndex({"appID":1})
db.team.createIndex({"userID": 1})
db.team.createIndex({"appID":1})
db.image.createIndex({"appID":1})
db.team.createIndex({"userID": 1, "appID":1,"permission":1 })
db.content.createIndex({key:1})
db.appevent.createIndex({"created":1})

db.invitation.createIndex({"hash": 1})
db.invitation.createIndex({"appID":1})
db.commandstack.createIndex({"appID":1})
db.comment.createIndex({"appID":1})
db.testsetting.createIndex({"appID":1})
db.user.createIndex({"email":1})
db.user.createIndex({"id":1})

```

## Development

You might need a mongo server. The simplest way is to use Docker.

```
docker run -p 27017:27017 --name quxmongo2 -d mongo:4.4

```

## Start server

java -jar server-3.20.0-fat.jar -conf matc.conf -instances 4

## Dev Setup

In InteliJ create a new runner with the following parameters:

- _Main Class_: io.vertx.core.Starter

- _Program Arguments_: run com.qux.MATC -conf matc.conf
