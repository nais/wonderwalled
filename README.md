# Wonderwalled

walled in by the wonderful [wonderwall](https://github.com/nais/wonderwall)

Basic toy API that requires (almost) all requests received to contain a Bearer token issued by the configured Identity Provider.
Expects the token to contain a claim `client_id` with a value that matches the client ID of the client that acquired the token.

## Endpoints:

- `/internal/*` - unauthenticated
  - `/internal/is_alive`
  - `/internal/is_ready`
- `/api/*` - requires a Bearer JWT access token in the `Authentication` header
  - `/api/headers` - prints all headers in the request
  - `/api/me` - prints all claims for the token received

## Requirements

- JDK 16

## Run

Set up [`application.properties`](src/main/resources/application.properties)

Then:

```shell
./gradlew run
```
