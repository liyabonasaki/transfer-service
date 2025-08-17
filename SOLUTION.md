### Authentication & Authorization

Currently, no authentication is required to interact with the Transfer Service APIs.  
However, the system has been designed in a way that allows authentication and authorization to be added later without significant refactoring.

#### Approach for Adding Auth Later
1. **Spring Security Integration**
    - We would introduce Spring Security as a middleware for securing endpoints.
    - Authentication could be based on **JWT tokens** issued by an identity provider (e.g., Keycloak, Okta, or a custom Auth service).
    - Endpoints can then be annotated with `@PreAuthorize` or `@RolesAllowed` to enforce role-based access.

2. **API Gateway / Reverse Proxy**
    - If part of a larger microservices ecosystem, authentication could also be managed at the API gateway layer (e.g., Kong, NGINX, or Spring Cloud Gateway).
    - This approach centralizes authentication and simplifies service-level code.

3. **Extensibility in Current Design**
    - The controller methods already validate headers (e.g., `Idempotency-Key`).
    - A similar mechanism could be used for an **Authorization header** without breaking existing functionality.
    - Service-level logic does not assume an "open" API; therefore, plugging in Spring Security filters would be straightforward.

#### Why This Approach?
- Keeps the current implementation **simple** for demonstration.
- Provides a clear **path to production readiness** by allowing token-based authentication later.
- Minimizes changes to controllers and services when auth is introduced.  
