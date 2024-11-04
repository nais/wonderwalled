# Wonderwalled

> Walled in by the wonderful [Wonderwall](https://github.com/nais/wonderwall)

Basic toy API in Ktor that showcases usage of Wonderwall from a backend application's point of view.
**This is not a production-ready application.**

Requires (almost) all requests received to contain a Bearer token issued by the configured Identity Provider.

[ID-porten](wonderwalled-idporten):

- Expects the token to contain a claim `aud` with a value that matches the client ID of the application's client.
- Supports Token Exchange using TokenX.

[Azure AD](wonderwalled-azure):

- Expects the token to contain a claim `aud` with a value that matches the client ID of the application's client.
- Supports the On-Behalf-Of flow.
- Supports the Client Credentials flow.

[Maskinporten](wonderwalled-maskinporten):

- Requires login with Azure AD.
- API for fetching machine-to-machine tokens from Maskinporten.

## Endpoints:

- `/internal/*` - unauthenticated
  - `/internal/is_alive`
  - `/internal/is_ready`
- `/api/*` - requires a Bearer JWT access token in the `Authorization` header
  - `/api/headers` - prints all headers in the request
  - `/api/me` - prints all claims for the token received
  - `/api/obo?aud=<cluster>:<namespace>:<app>` - exchanges the subject token for the given `aud` (audience)
  - `/api/m2m?aud=<cluster>:<namespace>:<app>` - (Azure only) fetches a machine-to-machine token for the given `aud` (audience)

## Development

Requires JDK installed, minimum version 21.

Start required dependencies:

```shell
docker-compose up -d
```

- [wonderwall](https://github.com/nais/wonderwall) @ <http://localhost:4000> (reverse proxy for openid connect)
- [texas](https://github.com/nais/texas) @ <http://localhost:3000> (token exchange / introspection service)
- [mock-oauth2-server](https://github.com/navikt/mock-oauth2-server) @ <http://localhost:8080> (mock identity provider)

Run wonderwalled for the desired identity provider:

```shell
./gradlew wonderwalled-azure:run
```

or

```shell
./gradlew wonderwalled-idporten:run
```

or

```shell
./gradlew wonderwalled-maskinporten:run
```

Visit the endpoints at `localhost:4000` (i.e. via Wonderwall as a reverse proxy):

- <http://localhost:4000>
- <http://localhost:4000/api/headers>
- <http://localhost:4000/api/me>
- <http://localhost:4000/api/obo?aud=cluster:namespace:app>
- <http://localhost:4000/api/m2m?aud=cluster:namespace:app>
