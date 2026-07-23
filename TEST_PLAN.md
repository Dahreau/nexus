# Test Plan: buy-01 (microservices + Angular scaffold)

This file lists concrete tests to answer the questions in your checklist. Each section maps to a question group from your prompt and contains test cases (objective, steps, test data, expected result, and acceptance criteria).

Notes / environment
- Project root: repository root (this file). The repo contains backend microservices and a minimal Angular scaffold.
- Expected service ports (from frontend/README):
  - user-service: http://localhost:8081
  - product-service: http://localhost:8082
  - media-service: http://localhost:8083
  - frontend dev server: http://localhost:4200
- Typical run command (dev): `docker-compose up --build` (run in project root). Use browser and Postman for API checks.

---

## 1) Initial Setup & Access
Purpose: verify the project starts via Docker and endpoints are reachable.

Test 1.1 — Start services and confirm startup
- Steps:
  1. From repo root run: `docker-compose up --build` (or use Docker Desktop to start compose).
  2. Watch logs for each service: user-service, product-service, media-service, and (if present) gateway/frontend container.
- Tools: terminal, Docker logs
- Expected result: All containers become healthy/ready; no crash loops; Spring Boot apps report started with port numbers.
- Acceptance criteria: Each backend logs `Started` message and no fatal exceptions during boot.

Test 1.2 — Confirm endpoints respond
- Steps:
  1. In browser / Postman open: `http://localhost:8081/` `http://localhost:8082/` `http://localhost:8083/` and frontend at `http://localhost:4200` (if frontend container exists) or run `npm start` locally and open `http://localhost:4200`.
  2. Check common health or root endpoint like `/actuator/health` or API root.
- Tools: browser, Postman
- Expected result: HTTP 200 or informative response; health endpoint shows UP (if actuator enabled).
- Acceptance criteria: All three backend endpoints respond (200/healthy) and frontend loads.

---

## 2) User & Product CRUD Operations
Purpose: test create, read, update, delete for users (client and seller) and products; check access control.

Test 2.1 — Create Client user (API)
- Steps:
  1. POST to `http://localhost:8081/api/auth/register` (or the register endpoint) with client payload: `{ "email": "client@test.local", "password": "P@ssw0rd", "role": "CLIENT", "name": "Client A" }`.
  2. Check response and GET user or query DB endpoint.
- Tools: Postman
- Expected result: 201 Created (or 200) and response contains user id and role=CLIENT.
- Acceptance criteria: User persisted and retrievable.

Test 2.2 — Create Seller user (API)
- Steps: same as 2.1 but role `SELLER` and different email.
- Expected result: Seller created with role SELLER.

Test 2.3 — Read users (list & detail)
- Steps:
  1. GET `/api/users` (or equivalent) and GET `/api/users/{id}` for created users.
- Expected result: endpoints return user lists and correct user details.

Test 2.4 — Update user
- Steps:
  1. PUT/PATCH to `/api/users/{id}` change `name` or other allowed fields.
  2. GET user and confirm change.
- Expected result: Updated fields persisted.

Test 2.5 — Delete user
- Steps:
  1. DELETE `/api/users/{id}` and then GET returns 404.
- Expected result: User removed or flagged as deleted, GET returns 404 or inactive.

Test 2.6 — Product CRUD as Seller
- Steps:
  1. Sign in as seller to get token.
  2. POST `/api/products` with product payload `{ "title": "Test product", "description": "desc", "price": 12.5 }` using Authorization Bearer token.
  3. GET `/api/products/{id}`, PUT `/api/products/{id}`, DELETE `/api/products/{id}`.
- Tools: Postman, frontend seller UI
- Expected result: Seller can create/update/delete products and product is returned in listing.
- Acceptance criteria: Product lifecycle works only when authenticated as seller.

Test 2.7 — Product create forbidden for client/anonymous
- Steps:
  1. Attempt POST `/api/products` without token and with client token.
- Expected result: 401 (anonymous) or 403 (client role) returned.
- Acceptance criteria: Only SELLER role allowed to create products.

---

## 3) Authentication & Role Validation
Purpose: ensure signup/login work and role-based permissions enforced.

Test 3.1 — Register and login flow (client)
- Steps:
  1. Register client (see 2.1).
  2. POST `/api/auth/login` with client credentials.
  3. Receive JWT token; decode (jwt.io) and check `roles` claim or equivalent.
- Expected result: token issued, expires at reasonable time, contains role CLIENT.
- Acceptance criteria: Successful login yields a valid JWT with role claim.

Test 3.2 — Register/login seller and test protected endpoints
- Steps:
  1. Register seller and login to obtain token.
  2. Use token for product creation (should succeed).
  3. Use client token for product creation (should fail).
- Expected result: Role-based access enforced.

Test 3.3 — Token expiry and refresh (if implemented)
- Steps:
  1. Inspect token expiry claim; if refresh exists, exercise refresh flow.
- Expected result: Expiry present; refresh endpoint works if implemented. If no refresh implemented, ensure expiry is reasonable.

---

## 4) Media Upload & Product Association
Purpose: test uploading images for products including size and type constraints and association to product.

Test 4.1 — Successful media upload (valid image)
- Steps:
  1. Login as seller and get token.
  2. Create a product.
  3. POST to media upload endpoint (e.g., `/api/media/upload` or `/api/products/{id}/media`) with `multipart/form-data` including an image file (jpeg/png) within allowed size limit.
  4. Check response for media id/url and then GET product details to verify association.
- Tools: Postman (form upload), frontend media manager
- Expected result: 201 and returned URL/id; product contains reference to uploaded media.
- Acceptance criteria: Upload stored and linked to product.

Test 4.2 — Reject invalid media type
- Steps:
  1. Upload `.exe` or `.txt` file as media.
- Expected result: 400 Bad Request with descriptive error about invalid type.

Test 4.3 — Reject oversized file
- Steps:
  1. Upload an image larger than allowed limit (e.g., if limit is 5MB upload a 10MB file).
- Expected result: 413/400 error and message about exceeding size limit.

Test 4.4 — Multiple images and association
- Steps:
  1. Attach multiple images to a product.
  2. Verify listing shows multiple media items and thumbnails (frontend).
- Expected result: All valid images accepted and associated.

---

## 5) Frontend Interaction
Purpose: exercise UI flows and validate UX, token handling, and integration with backend.

Test 5.1 — Sign up & Sign in (UI)
- Steps:
  1. Open frontend at `http://localhost:4200`.
  2. Use Register page to create a client and a seller.
  3. Use Login page to sign in; confirm navigation and token stored in localStorage (or cookie).
- Tools: browser devtools, localStorage inspection
- Expected result: After login user is routed appropriately and token saved.

Test 5.2 — Seller dashboard and product management UI
- Steps:
  1. Login as seller and open seller dashboard.
  2. Create product via UI, edit, delete.
- Expected result: UI calls API successfully and updates lists.

Test 5.3 — Product listing page (for clients)
- Steps:
  1. Login as client (or use anonymous) and browse product list.
  2. Verify product details page shows media and info.
- Expected result: Listing and detail pages render data from API with images.

Test 5.4 — Media manager UI
- Steps:
  1. From seller UI upload images and observe client-side validation (type/size), progress indicator, and resulting gallery.
- Expected result: Client side prevents invalid uploads and displays success.

UX Acceptance Criteria:
- Buttons and forms work, errors surfaced, token persisted, and user is shown the correct role-only pages.

---

## 6) Security
Purpose: validate password hashing, input validation, exposure of secrets, and (if present) HTTPS usage.

Test 6.1 — Password hashing verification
- Steps:
  1. Register a user.
  2. Inspect the persistence store (if accessible) or check DB documents to confirm password is hashed (not plain text).
- Expected result: password stored as a bcrypt/argon2 hash, not raw password.

Test 6.2 — Sensitive data not in responses
- Steps:
  1. Call user endpoints and confirm no sensitive fields (password, secret keys) are returned.
- Expected result: Passwords never returned in API responses.

Test 6.3 — Input validation / injection tests
- Steps:
  1. Attempt NoSQL injection strings in inputs (e.g., `{"$gt": ""}`) and form fields.
  2. Attempt XSS payload in product description and check if UI escapes it.
- Expected result: Inputs validated or sanitized; no arbitrary query change and content safely escaped in UI.

Test 6.4 — Transport security
- Steps:
  1. Check if HTTPS is configured for production images or any reverse proxy. For local Docker dev, HTTPS often not enabled.
- Expected result: For dev, HTTP ok; for production images check docs; lack of HTTPS in dev not a failure but should be documented.

Acceptance Criteria: Passwords hashed, no secrets leaked in logs or responses, inputs validated, and roles enforced server-side.

---

## 7) Code Quality and Standards (Backend)
Purpose: inspect use of Spring Boot, MongoDB, and common annotations and patterns.

Test 7.1 — Annotation sanity pass
- Steps:
  1. Review backend controller classes for `@RestController`, `@RequestMapping`, and endpoints.
  2. Check services for `@Service`, repositories for `@Repository` or `MongoRepository`, and models for `@Document` (if using MongoDB).
- Expected result: Correct annotations used; layer separation maintained.

Test 7.2 — Security annotations
- Steps:
  1. Check `@PreAuthorize` / `@RolesAllowed` or method-level security on controllers for role checks. Verify config in `security` package.
- Expected result: Protected endpoints annotated or protected via security config.

Test 7.3 — Error handling patterns
- Steps:
  1. Check for `@ControllerAdvice` or exception handlers to format API errors.
- Expected result: Centralized exception handling exists returning helpful structured errors.

Deliverable: Note any missing/broken patterns and list concrete files/lines to fix.

---

## 8) Frontend Implementation (Angular)
Purpose: review components, services, DI patterns, and usage of reactive forms / http interceptors.

Test 8.1 — Component structure and routing
- Steps:
  1. Inspect `src/app` files in scaffold: `app.module.ts`, `app.component.ts`, `login`, `register`, `seller-dashboard`, `product-list`, `media-manager`.
  2. Verify routing is present and lazy-loading considered (if implemented).
- Expected result: Modules and components imported and declared; routing maps to components.

Test 8.2 — Services and token interceptor
- Steps:
  1. Open `auth.service.ts`, `token.interceptor.ts`, `product.service.ts`, `media.service.ts` and confirm:
     - auth stores token in localStorage
     - token interceptor attaches Authorization header
     - services use `HttpClient` and handle errors (catchError)
- Expected result: Interceptor sets Bearer token; services structured and reuse methods appropriately.

Test 8.3 — Form validation and client-side checks
- Steps:
  1. Inspect login/register forms and media upload UI for client-side validation (required, pattern, file size/type checks).
- Expected result: Forms implement validation and show helpful messages.

---

## 9) Error Handling & Edge Cases
Purpose: validate application handles erroneous inputs and returns useful errors.

Test 9.1 — Duplicate email registration
- Steps:
  1. Register with `dup@test.local`.
  2. Attempt registering again with same email.
- Expected result: 400/409 status and clear message about duplicate email.

Test 9.2 — Invalid media type/oversize (re-test)
- Steps: repeat 4.2 and 4.3.
- Expected result: 4xx response with descriptive message.

Test 9.3 — Unauthorized actions
- Steps:
  1. As client, attempt to delete someone else's product or upload media.
- Expected result: 403 Forbidden and clear message.

Test 9.4 — Missing/invalid JWT
- Steps:
  1. Send request with malformed token or expired token to protected endpoints.
- Expected result: 401 with appropriate error message.

---

## How to use this plan
1. Start with `Test 1.1` to ensure the environment is healthy.
2. Run the API tests (Postman collections) for CRUD and auth (sections 2–4) — these will validate the core functionality.
3. Exercise frontend UI tests (section 5) and re-run corresponding API calls to confirm behavior.
4. Run security and edge-case tests and capture logs for failed/security issues.

## Quick Postman checklist items to create
- Collection: `buy-01 - Backend` with folders: `Auth`, `User`, `Product`, `Media`.
- Environment variables: base URLs and tokens for client and seller (clientToken, sellerToken).

---

## Acceptance summary (per original questions)
- Each test has an acceptance criteria. When all pass, the answer to each original prompt is "Yes — the app runs and behaves as expected." If some fail, attach failing test id(s) and logs for remediation.

---

If you want, I can:
- Generate a basic Postman collection (JSON) with the endpoints referenced here.
- Run the initial Docker Compose locally in this environment and mark tests in the todo list as in-progress/completed while I run them.

