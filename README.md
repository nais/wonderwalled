# Wonderwalled

> Walled in by the wonderful [Wonderwall](https://github.com/nais/wonderwall)

Basic toy API in Ktor that showcases usage of Wonderwall from a backend application's point of view.
**This is not a production-ready application.**

Requires (almost) all requests received to contain a Bearer token issued by the configured Identity Provider.

[ID-porten](idporten):
- Expects the token to contain a claim `client_id` with a value that matches the client ID of the application's client.
- Supports Token Exchange using TokenX.

[Azure AD](azure):
- Expects the token to contain a claim `aud` with a value that matches the client ID of the application's client.
- Supports the On-Behalf-Of flow.
- Supports the Client Credentials flow.

## Endpoints:

- `/internal/*` - unauthenticated
  - `/internal/is_alive`
  - `/internal/is_ready`
- `/api/*` - requires a Bearer JWT access token in the `Authorization` header
  - `/api/headers` - prints all headers in the request
  - `/api/me` - prints all claims for the token received
  - `/api/obo?aud=<cluster>:<namespace>:<app>` - exchanges the subject token for the given `aud` (audience)
  - `/api/m2m?aud=<cluster>:<namespace>:<app>` - (Azure only) fetches a machine-to-machine token for the given `aud` (audience)
