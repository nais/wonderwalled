# Wonderwalled

> Walled in by the wonderful [Wonderwall](https://github.com/nais/wonderwall)

Basic toy API in Ktor that showcases usage of Wonderwall from a backend application's point of view.
**This is not a production-ready application.**

Requires (almost) all requests received to contain a Bearer token issued by the configured Identity Provider.

[Azure AD](wonderwalled-azure):

- Expects the token to contain a claim `aud` with a value that matches the client ID of the application's client.
- Supports the On-Behalf-Of flow.
- Supports the Client Credentials flow.

[ID-porten](wonderwalled-idporten):

- Expects the token to contain a claim `aud` with a value that matches the client ID of the application's client.
- Supports Token Exchange using TokenX.

[Maskinporten](wonderwalled-maskinporten):

- Requires login with Azure AD.
- API for fetching machine-to-machine tokens from Maskinporten.

## Endpoints

Common endpoints for all identity providers:

- `/internal/*` - unauthenticated
  - `GET /internal/is_alive`
  - `GET /internal/is_ready`
- `/api/*` - requires a Bearer JWT access token in the `Authorization` header
  - `GET /api/headers` - prints all headers in the request
  - `GET /api/me` - prints all claims for the authenticated user's token

### Endpoints for wonderwalled-azure

- `/api/*` - requires a Bearer JWT access token in the `Authorization` header
  - `GET /api/obo?aud=<cluster>:<namespace>:<app>` - exchanges the authenticated user's token for the given `aud` (audience)
  - `GET /api/m2m?aud=<cluster>:<namespace>:<app>` - returns a machine-to-machine token for the given `aud` (audience)

### Endpoints for wonderwalled-idporten

- `/api/*` - requires a Bearer JWT access token in the `Authorization` header
  - `GET /api/obo?aud=<cluster>:<namespace>:<app>` - exchanges the authenticated user's token for the given `aud` (audience)

### Endpoints for wonderwalled-maskinporten

- `/api/*` - requires a Bearer JWT access token in the `Authorization` header
  - `GET /api/token[?scope=nav:test/api]` - returns a machine-to-machine token with the given scope
  - `GET /api/introspect[?scope=nav:test/api]` - returns the introspection result for a machine-to-machine token with the given scope

## Development

Requires JDK installed, minimum version 25.

```shell
make azure
```

or

```shell
make idporten
```

or

```shell
make maskinporten
```

This starts up required dependencies with docker-compose:

- [wonderwall](https://github.com/nais/wonderwall) @ <http://localhost:4000> (reverse proxy for openid connect)
- [texas](https://github.com/nais/texas) @ <http://localhost:3000> (token exchange / introspection service)
- [mock-oauth2-server](https://github.com/navikt/mock-oauth2-server) @ <http://localhost:7070> (mock identity provider)

and then runs Wonderwalled for the chosen identity provider.

Visit the endpoints at `localhost:4000` (i.e. via Wonderwall as a reverse proxy):

- <http://localhost:4000>
- <http://localhost:4000/api/headers>
- <http://localhost:4000/api/me>
- <http://localhost:4000/api/obo?aud=cluster:namespace:app>
- <http://localhost:4000/api/m2m?aud=cluster:namespace:app>
