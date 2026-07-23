## Test Report & Key Code Snippets

This file collects the relevant code snippets and short findings to answer the test-plan questions (Initial setup, CRUD, Auth, Media, Frontend, Security, Code Quality, Error handling).

Each section contains the most relevant server- and client-side snippets and a short answer.

---

### Initial Setup & Access

- Docker compose exposes services:

```yaml
services:
  user-service: 8081
  product-service: 8082
  media-service: 8083
  mongo: 27017
```

Location: `docker-compose.yml`

Quick run notes:
- Start backend & DB with: `docker-compose up --build -d` (run in repo root).
- Backend APIs:
  - User/Auth: http://localhost:8081/api/auth
  - Products: http://localhost:8082/api/products
  - Media: http://localhost:8083/api/media

Can be tested with a browser (GET endpoints) and Postman for POST/PUT/DELETE. Frontend runs separately via Angular dev server (not in docker-compose).

Finding: Services are configured for Docker; port mapping and volumes are present for `media-service/uploads`.

---

### User & Product CRUD Operations

Relevant backend snippets (User authentication & password hashing):

File: `backend/user-service/src/main/java/com/example/userservice/controller/AuthController.java`

```java
@PostMapping("/register")
public ResponseEntity<?> register(@RequestBody Map<String, String> body) {
    String name = body.get("name");
    String email = body.get("email");
    String password = body.get("password");
    String roleStr = body.get("role");
    Role role = "SELLER".equalsIgnoreCase(roleStr) ? Role.SELLER : Role.CLIENT;

    if (userRepository.findByEmail(email).isPresent()) {
        return ResponseEntity.badRequest().body(Map.of("error", "Email already in use"));
    }
    String hashed = passwordEncoder.encode(password);
    User u = new User(name, email, hashed, role);
    userRepository.save(u);
    String token = JwtUtil.generateToken(u.getId(), u.getRole().name());
    return ResponseEntity.ok(Map.of("token", token, "userId", u.getId()));
}
```

Product CRUD (create/update/delete protected for sellers):

File: `backend/product-service/src/main/java/com/example/productservice/controller/ProductController.java`

```java
// create product - only seller
@PostMapping
public ResponseEntity<?> create(@RequestBody Product p) {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth == null || auth.getAuthorities().stream().noneMatch(a -> a.getAuthority().equalsIgnoreCase("ROLE_SELLER"))) {
        return ResponseEntity.status(403).body(Map.of("error", "Only sellers can create products"));
    }
    String userId = auth.getName();
    p.setUserId(userId);
    Product saved = repo.save(p);
    return ResponseEntity.ok(saved);
}

@PutMapping("/{id}")
public ResponseEntity<?> update(@PathVariable String id, @RequestBody Product p) {
    // checks seller role and owner match
}

@DeleteMapping("/{id}")
public ResponseEntity<?> delete(@PathVariable String id) {
    // checks seller role and owner match
}
```

Short answer: CRUD operations exist for Products. The controller enforces seller-only access for create/update/delete by checking authorities and matching the authenticated user's id to the product's userId.

User CRUD (beyond register/login) is handled in repository/models; registration hashes passwords using BCrypt.

---

### Authentication & Role Validation

JWT generation on registration/login:

File: `backend/user-service/src/main/java/com/example/userservice/controller/AuthController.java` (login snippet)

```java
@PostMapping("/login")
public ResponseEntity<?> login(@RequestBody Map<String, String> body) {
    String email = body.get("email");
    String password = body.get("password");
    var opt = userRepository.findByEmail(email);
    if (opt.isEmpty()) return ResponseEntity.status(401).body(Map.of("error", "Invalid credentials"));
    User u = opt.get();
    if (!passwordEncoder.matches(password, u.getPassword())) {
        return ResponseEntity.status(401).body(Map.of("error", "Invalid credentials"));
    }
    String token = JwtUtil.generateToken(u.getId(), u.getRole().name());
    return ResponseEntity.ok(Map.of("token", token, "userId", u.getId()));
}
```

Security configuration registers a JWT filter and allows `/api/auth/**` to be public:

File: `backend/user-service/src/main/java/com/example/userservice/security/SecurityConfig.java`

```java
.authorizeHttpRequests()
.requestMatchers("/api/auth/**").permitAll()
.anyRequest().authenticated()
.and()
.addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);
```

Client-side token processing:

File: `frontend/src/app/services/auth.service.ts`

```typescript
isSeller(): boolean {
  const token = this.getToken();
  if (!token) return false;
  try {
    const payload = JSON.parse(atob(token.split('.')[1]));
    return payload?.role === 'SELLER';
  } catch { return false; }
}

getUserId(): string | null {
  const token = this.getToken();
  if (!token) return null;
  try {
    const payload = JSON.parse(atob(token.split('.')[1]));
    return payload?.sub || payload?.userId || null;
  } catch { return null; }
}
```

Short answer: Authentication uses BCrypt for passwords and JWT tokens. Role is embedded in the JWT. Backend security filters JWTs and protects endpoints; the frontend reads JWT payload to enable/disable UI for seller-specific flows.

---

### Media Upload & Product Association

Media upload logic (validation + save + best-effort product-service notify):

File: `backend/media-service/.../MediaController.java`

```java
@PostMapping("/upload")
public ResponseEntity<?> upload(@RequestParam("file") MultipartFile file,
                                @RequestParam("productId") String productId) {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth == null || auth.getAuthorities().stream().noneMatch(a -> a.getAuthority().equalsIgnoreCase("ROLE_SELLER"))) {
        return ResponseEntity.status(403).body(Map.of("error", "Only sellers can upload media"));
    }

    if (file.isEmpty()) return ResponseEntity.badRequest().body(Map.of("error", "Empty file"));
    if (file.getSize() > 2L * 1024 * 1024) return ResponseEntity.badRequest().body(Map.of("error", "File too large (max 2MB)"));
    String contentType = file.getContentType();
    if (contentType == null || !(contentType.equalsIgnoreCase("image/png") || contentType.equalsIgnoreCase("image/jpeg") || contentType.equalsIgnoreCase("image/gif"))) {
        return ResponseEntity.badRequest().body(Map.of("error", "Unsupported file type"));
    }

    // save file to uploads/ and create Media entry, then attempt to notify product-service via
    // POST /api/products/{productId}/images with X-Internal-Token header
}
```

Product association endpoint (accepts internal token):

File: `backend/product-service/.../ProductController.java`

```java
@PostMapping("/{id}/images")
public ResponseEntity<?> addImage(@PathVariable String id, @RequestBody Map<String, String> body,
                                  @RequestHeader(value = "X-Internal-Token", required = false) String token) {
    String internalToken = System.getenv("INTERNAL_TOKEN");
    if (internalToken == null || !internalToken.equals(token)) {
        return ResponseEntity.status(403).body(Map.of("error", "Forbidden"));
    }
    // append mediaId to product.imageIds
}
```

Client-side upload (validations mirrored):

File: `frontend/src/app/media-manager.component.ts`

```typescript
if (!this.file) { this.error = 'Choose a file'; return; }
if (this.file.size > 2 * 1024 * 1024) { this.error = 'File too large (max 2MB)'; return; }
const allowed = ['image/png','image/jpeg','image/gif'];
if (!allowed.includes(this.file.type)) { this.error = 'Unsupported file type'; return; }
const token = localStorage.getItem('token');
// sends form data to http://localhost:8083/api/media/upload with Authorization header
```

Short answer: Media upload enforces a 2MB limit and restricts types (png, jpeg, gif) server-side and client-side. Media-service saves files to `uploads/` and stores a Database record; it attempts to notify product-service using an internal shared token to associate the media with a product.

---

### Frontend Interaction

Relevant client flow pieces:

- `frontend/src/app/register.component.ts` -> calls `AuthService.register(...)` and stores token on success.
- `frontend/src/app/login.component.ts` -> calls `AuthService.login(...)` and stores token.
- `frontend/src/app/services/token.interceptor.ts` -> attaches `Authorization: Bearer <token>` to outgoing requests.
- `frontend/src/app/media-manager.component.ts` -> UI for file selection, validation and upload.
- `frontend/src/app/seller-dashboard.component.ts` and `product-list.component.ts` -> product management and listing (product create/update use ProductService).

Short answer: The frontend provides sign-in/up, product listing, seller product management and a media upload manager. Token interceptor ensures authenticated requests include the JWT. UI uses simple forms and basic feedback via `error` fields or alerts.

---

### Security

Server-side security highlights:

- Passwords: BCrypt hashing via `BCryptPasswordEncoder` (see `user-service/security/SecurityConfig.java` and `AuthController`).
- JWT: tokens are generated (`JwtUtil`) and a `JwtAuthFilter` is added to the filter chain. `/api/auth/**` is permitted while other endpoints require authentication.
- Role checks: controllers check role authorities (e.g., `ROLE_SELLER`) before allowing create/update/delete or upload operations.
- Internal service-to-service authorization: `X-Internal-Token` environment variable used to authorize internal calls to product service.

HTTPS / TLS guidance (what's missing and how to add it)

The current `docker-compose.yml` and services expose plain HTTP on the mapped ports (8081/8082/8083). For production (and for secure local testing) you should terminate TLS in front of the services. Below are two practical approaches and minimal examples you can adopt depending on your needs.

1) Local development — self-signed cert + nginx reverse proxy

- Generate a self-signed certificate (requires OpenSSL):

```powershell
# run in PowerShell (you need openssl installed)
openssl req -x509 -nodes -days 365 -newkey rsa:2048 -keyout certs/server.key -out certs/server.crt -subj "/CN=localhost"
```

- Minimal `nginx` config (save as `nginx.conf`):

```nginx
events {}
http {
    server {
        listen 443 ssl;
        server_name localhost;
        ssl_certificate /etc/ssl/certs/server.crt;
        ssl_certificate_key /etc/ssl/certs/server.key;

        location / {
            proxy_pass http://frontend:4200; # or a backend API depending on host/path
            proxy_set_header Host $host;
            proxy_set_header X-Real-IP $remote_addr;
            proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        }
    }
}
```

- Compose fragment to add the nginx proxy (mount `certs/` and `nginx.conf`):

```yaml
    nginx-proxy:
        image: nginx:stable
        ports:
            - "443:443"
        volumes:
            - ./certs:/etc/ssl/certs:ro
            - ./nginx.conf:/etc/nginx/nginx.conf:ro
        depends_on:
            - user-service
            - product-service
            - media-service
```

Notes: use this only for local testing. Browsers will warn about the self-signed certificate unless you add it to the OS/browser trust store.

2) Production — Traefik (recommended for Docker-based deployments)

- Traefik can automatically obtain and renew TLS certificates (Let's Encrypt) and route to the internal Docker services using labels. A minimal `docker-compose` service for Traefik looks like:

```yaml
    traefik:
        image: traefik:v2.10
        command:
            - --api.insecure=false
            - --providers.docker=true
            - --entrypoints.web.address=:80
            - --entrypoints.websecure.address=:443
            - --certificatesresolvers.le.acme.tlschallenge=true
            - --certificatesresolvers.le.acme.email=you@example.com
            - --certificatesresolvers.le.acme.storage=/letsencrypt/acme.json
        ports:
            - "80:80"
            - "443:443"
        volumes:
            - /var/run/docker.sock:/var/run/docker.sock:ro
            - ./letsencrypt:/letsencrypt
```

- Then add labels to your services (example for `user-service`) so Traefik will route TLS traffic to them:

```yaml
    user-service:
        build: ./backend/user-service
        labels:
            - "traefik.http.routers.user.rule=Host(`api.example.com`)"
            - "traefik.http.routers.user.entrypoints=websecure"
            - "traefik.http.routers.user.tls.certresolver=le"
            - "traefik.http.services.user.loadbalancer.server.port=8081"
```

Notes: Traefik requires a public domain name (DNS) pointing to your host for Let's Encrypt to work. For internal/private deployments you can use your own CA or provision certs from a certificate manager.

Additional recommendations:
- Offload TLS at a reverse proxy to keep the application containers simple.
- Protect the internal network (Docker bridge) and avoid exposing internal database ports to the public internet.
- Store secrets (JWT_SECRET, INTERNAL_TOKEN) in a secret manager or Docker secrets rather than plaintext in `docker-compose.yml`.

If you want, I can:
- add a `nginx` proxy compose service and sample `nginx.conf` plus a short PowerShell script to generate self-signed certs for local dev, or
- add a reference `traefik` service and show how to label each backend service for TLS routing.

Sensitive data handling:
- JWT secret is provided via environment variable `JWT_SECRET` (in docker-compose). Ensure you set strong values before production.

Short answer: Basic security practices are present (BCrypt, JWT, role enforcement). No TLS in the compose setup — traffic is over HTTP inside host. Internal token pattern is present but must be rotated/kept secret.

---

### Code Quality and Standards

Observations (backend):

- Uses Spring annotations (`@RestController`, `@RequestMapping`, `@PostMapping`, etc.) correctly in controllers.
- Security is configured per-module with `SecurityFilterChain` and CORS configured.
- Business logic is placed in controllers; small services/reusable layers are not obvious — the code is straightforward but could be improved by extracting service classes for clearer separation.
- Product-service uses `SecurityContextHolder` to check the authenticated user and its authorities — a common pattern when not using method-level `@PreAuthorize`.

Short answer: Spring Boot annotations are used appropriately. There are opportunities to extract commonly used logic (e.g., role checks, exception handling) into services or controller advice.

---

### Frontend Implementation

Observations:

- Angular app uses services for HTTP (AuthService, ProductService, MediaService) and a TokenInterceptor to attach JWT.
- Components are small and template-driven (no ReactiveForms). That's acceptable for a simple admin flow but may grow fragile.
- Media upload uses FormData and headers to attach Authorization when manually called (media-manager uses direct HttpClient call with headers; media service `upload()` does not itself add Authorization — the interceptor handles headers for most calls but media-manager sets headers manually).

Short answer: The Angular structure is logical: services + components + interceptor. There is some duplication (manual header set in media-manager + interceptor) that can be unified.

---

### Error Handling & Edge Cases

Key code paths for error handling:

- Register with existing email -> returns 400 with `Email already in use` (server enforces uniqueness check).
- Login with wrong credentials -> 401 `Invalid credentials`.
- Upload invalid file (empty, >2MB, wrong type) -> 400 errors with meaningful messages.
- Product operations by non-seller or mismatched owner -> 403 with error messages.

Examples:

```java
// upload file too large
if (file.getSize() > 2L * 1024 * 1024) return ResponseEntity.badRequest().body(Map.of("error", "File too large (max 2MB)"));

// register existing email
if (userRepository.findByEmail(email).isPresent()) {
    return ResponseEntity.badRequest().body(Map.of("error", "Email already in use"));
}
```

Short answer: The application handles many expected edge cases with clear HTTP statuses and messages. A few places swallow downstream errors (e.g., media->product notify wrapped in try/catch and ignored, which is intentional) — this is acceptable for best-effort sync but should be logged.

---

## Quick verification checklist (what I checked)

- Verified presence of `BCryptPasswordEncoder` and usage in `AuthController`.
- Verified JWT filter + SecurityFilterChain in each service module.
- Verified product create/update/delete enforce seller role and ownership.
- Verified media upload enforces size/type and attempts product-service sync using `X-Internal-Token`.
- Verified frontend services/components and token interceptor behaviour.

## Next steps / Recommendations

- Run `docker-compose up --build -d` and the frontend with `npm install` + `ng serve` to fully exercise the UI.
- Consider centralizing Authorization checks using `@PreAuthorize` and a shared `@ControllerAdvice` for consistent error responses.
- Add server-side logging around media->product notification failures for visibility.
- For production, add HTTPS termination and stronger secret management (vault or environment injection), and rotate `INTERNAL_TOKEN` regularly.

---

### HTTPS integration files added (local dev)

I added a minimal local-HTTPS integration that terminates TLS using an nginx reverse proxy. Files added to the repository:

- `nginx.conf` — TLS-terminating nginx config that proxies API routes to the three backend services and `/` to the frontend (if containerized).
- `certs/generate-self-signed.ps1` — PowerShell helper script to create a self-signed cert using OpenSSL.
- `docker-compose.override.yml` — composes an `nginx-proxy` service which mounts `nginx.conf` and `certs/`, exposing port 443.

If you prefer Traefik or a different TLS setup, I can add a production-ready example as well.

### Detailed Changes Performed (by me)

Below is a concise summary of the repository edits and runtime actions I performed to enable local HTTPS and improve security posture for the dev stack.

- **Files added**:
    - `frontend/Dockerfile` — containerizes the Angular dev server so `nginx` can reliably proxy to `frontend:4200` inside Docker.
    - `docker-compose.override.yml` — runs an `nginx-proxy` that binds host ports `80`/`443` and mounts `./certs` and `nginx.conf`.

- **Files modified or added to repo**:
    - `nginx.conf` — updated to terminate TLS, add security headers (HSTS, X-Frame-Options, X-Content-Type-Options, Referrer-Policy, X-XSS-Protection), forward `X-Forwarded-*` headers, and route `/` to the frontend and `/api/*` to backend services.
    - `backend/*/src/main/resources/application.properties` — added `server.forward-headers-strategy=native` and comments recommending JWT secret via env (no fallback in production).
    - `backend/*/security/SecurityConfig.java` — tightened CORS: restricted allowed origins to local development origins (e.g. `https://localhost`, `http://localhost`, `https://127.0.0.1`) and restricted allowed headers.

- **Runtime actions performed**:
    - Generated a self-signed certificate pair placed under `./certs` using the provided PowerShell helper `.\\certs\\generate-self-signed.ps1` (this creates `server.crt` and `server.key`).
    - Removed an orphan/duplicate `nginx` container that caused a port `443` bind conflict, to allow the `nginx-proxy` in `docker-compose.override.yml` to bind host `443` successfully.
    - Containerized the frontend (added `frontend` service) instead of relying on a host dev server; this avoids host->container reachability issues and lets `nginx` proxy to `frontend:4200` by service name.
    - Rebuilt and started the full stack with Docker Compose and tested TLS termination.

- **Verification**:
    - Start the stack (PowerShell):

```powershell
.\certs\generate-self-signed.ps1    # (if certs not already present)
docker-compose up --build -d
```

    - Quick TLS check (PowerShell / curl):

```powershell
curl.exe -k -v https://localhost/
```

    - Expected result: HTTP/1.1 200 OK with the Angular HTML returned and security headers present (e.g. `Strict-Transport-Security`, `X-Frame-Options`, `X-Content-Type-Options`). The `-k` flag bypasses self-signed cert warnings for quick verification.

- **Key recommendations & next steps**:
    - **Secrets**: Replace any hard-coded JWT fallback secret — require `JWT_SECRET` via environment (or use Docker secrets / a vault) before exposing the stack outside local dev.
    - **Internal token**: Keep `INTERNAL_TOKEN` secret (use Docker secrets) and rotate it periodically.
    - **Production TLS**: For production or more realistic certs, consider Traefik with Let's Encrypt (I can add an example `docker-compose.traefik.yml`).
    - **Trusting the cert**: To avoid browser warnings in local development, import `certs/server.crt` into your OS/browser trust store (only for local testing).

If you want, I can also add a short README snippet showing how to trust `certs/server.crt` on Windows and how to switch to Traefik for Let’s Encrypt.

### How to test locally (Windows PowerShell)

1) Generate self-signed certificate (requires OpenSSL in PATH):

```powershell
.\certs\generate-self-signed.ps1    # If you named the script differently, run .\certs\generate-self-signed.ps1
```

2) Start the full stack (the override file adds the nginx proxy automatically):

```powershell
> docker-compose up --build -d
```

3) Open https://localhost in your browser. The nginx proxy listens on 443 and will forward API requests to the proper backend containers.

Notes & troubleshooting:
- Browser will warn about the self-signed certificate. To remove the warning, add `certs/server.crt` to your OS/browser trust store (only for local dev).
- If your Angular dev server runs on the host (not inside Docker with name `frontend`), update `nginx.conf` to use `proxy_pass http://host.docker.internal:4200;` for the `/` location, or run the frontend as a container named `frontend`.
- If you see `502` from nginx for backend routes, confirm containers are healthy and visible on the `buy-net` Docker network and that service names match (`user-service`, `product-service`, `media-service`).

If you want, I can also add a small README section or a `docker-compose.traefik.yml` sample for production TLS with Let's Encrypt.
