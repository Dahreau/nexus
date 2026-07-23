# Sécurité — buy-02

## 1. Authentification : JWT signé, vérifié partout

Il n'y a qu'un seul endroit où un mot de passe existe : `user-service`. Le flux :

1. `POST /api/auth/register` ou `/login` sur `user-service` — `backend/user-service/src/main/java/com/example/userservice/controller/AuthController.java`.
2. Le mot de passe est comparé/hashé avec **BCrypt** — `AuthController.java:22` (`private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();`), utilisé à l'inscription (`AuthController.java:40`, `passwordEncoder.encode(password)`) et à la connexion (`AuthController.java:56`, `passwordEncoder.matches(password, u.getPassword())`) — jamais stocké en clair.
3. En cas de succès, `user-service` génère un **JWT (HS256)** — `AuthController.java:43` et `:59` (`JwtUtil.generateToken(u.getId(), u.getRole().name(), u.getName())`), construit dans `backend/user-service/src/main/java/com/example/userservice/security/JwtUtil.java:24-28`, contenant trois informations utiles :
   - `sub` : l'id de l'utilisateur
   - `role` : `CLIENT` ou `SELLER`
   - `name` : le nom affiché (sert à afficher "connecté en tant que ..." et le nom du vendeur sur ses produits)
4. Le token est valable **24h** — `JwtUtil.java:17` (`EXP_MS = 1000L * 60 * 60 * 24`).

Les 4 autres services (`product`, `media`, `order`, `cart`) ne réémettent jamais de token et n'ont pas de notion de mot de passe. Chacun a son propre `JwtAuthFilter` (même pattern répété × 5, référence ci-dessous pour `product-service` — `backend/product-service/src/main/java/com/example/productservice/security/JwtAuthFilter.java`) qui :
1. Lit l'en-tête `Authorization: Bearer <token>` — `JwtAuthFilter.java:19`.
2. Vérifie la signature du token avec `JwtUtil.parseToken(...)` — `JwtAuthFilter.java:23`.
3. Si valide, construit un objet `Authentication` Spring Security avec l'id utilisateur comme *principal* et `ROLE_<role>` comme autorité — `JwtAuthFilter.java:28-29` (`new UsernamePasswordAuthenticationToken(...)`, `SecurityContextHolder.getContext().setAuthentication(auth)`) — **sans jamais recontacter user-service**.

Ça ne marche que parce que **les 5 services partagent le même secret de signature**, via la variable d'environnement `JWT_SECRET` — `docker-compose.yml:73,92,111,136,159` (`JWT_SECRET=${JWT_SECRET}`, une ligne identique par service). Si ce secret diffère d'un service à l'autre, la validation du token échoue silencieusement (l'utilisateur est traité comme non authentifié) — c'est la cause la plus probable si un service renvoie systématiquement 401/403 alors que les autres acceptent le même token.

## 2. Autorisation par rôle

Il y a deux rôles : `CLIENT` et `SELLER`. La vérification du rôle se fait **au niveau du contrôleur**, pas au niveau des règles Spring Security globales (sauf pour rendre `GET /api/products/**` public). Exemples :

- `ProductController.validateSeller()` — `backend/product-service/src/main/java/com/example/productservice/controller/ProductController.java:171` : lève une 403 si l'utilisateur authentifié n'a pas `ROLE_SELLER`, appelée avant toute création (`:60`), modification (`:76`) ou suppression (`:93`) de produit.
- `OrderController.isSeller()` — `backend/order-service/src/main/java/com/example/orderservice/controller/OrderController.java:45` : même logique pour les endpoints réservés aux vendeurs, vérifiée aux lignes `:57`, `:92`, `:107` (`/api/orders/seller`, `/api/orders/{id}/status`, etc.).

Un `SELLER` peut aussi être acheteur (voir [architecture.md](./architecture.md)) : `clientGuard` côté frontend (`frontend/src/app/services/auth.guard.ts:28-36`) autorise n'importe quel utilisateur connecté à accéder à "Mes commandes", et `cart-service` empêche seulement d'acheter **son propre** produit — `backend/cart-service/src/main/java/com/example/cartservice/service/CartService.java:42-44` (`if (userId.equals(product.getUserId())) throw ...`).

## 3. Jeton interne (service-à-service)

Certains endpoints ne doivent être appelés que par un autre service de confiance, jamais directement par un utilisateur ou le frontend :

- `POST /api/products/{id}/images` (product-service) — appelé par media-service après un upload.
- `POST /api/products/stock-update` (product-service) — appelé par order-service quand une commande est payée.

Ces endpoints sont protégés par un en-tête `X-Internal-Token`, comparé à la variable d'environnement `INTERNAL_TOKEN` (même valeur partagée entre tous les services, comme `JWT_SECRET`) — `backend/product-service/src/main/java/com/example/productservice/controller/ProductController.java:102-104` pour l'endpoint images, `:125-127` pour l'endpoint stock. Ce n'est **pas** un JWT : c'est un simple secret partagé vérifié par égalité stricte (`internalToken.equals(token)`, `ProductController.java:104`). Si l'en-tête est absent ou ne correspond pas, l'appel est rejeté (403), même si le reste de la route est marquée `permitAll()` au niveau Spring Security.

C'est un choix pragmatique pour un projet de cette taille (pas de mTLS, pas de service mesh) : la vraie barrière de sécurité, c'est que ces routes ne sont normalement joignables que depuis le réseau Docker interne — le token interne est une deuxième ligne de défense.

## 4. CORS

Chaque service Spring Security expose son propre `CorsConfigurationSource`, avec la même politique — un fichier par service : `backend/product-service/.../security/SecurityConfig.java`, `backend/order-service/.../security/SecurityConfig.java`, `backend/cart-service/.../security/SecurityConfig.java`, `backend/media-service/.../security/SecurityConfig.java`, `backend/user-service/.../security/SecurityConfig.java`.

- Origines autorisées : `localhost`/`127.0.0.1`, avec ou sans le port `4200`, en `http` et `https` (couvre `ng serve` en dev et un déploiement derrière nginx).
- Méthodes : `GET, POST, PUT, DELETE, OPTIONS`.
- En-têtes autorisés : `Authorization, Content-Type, Accept`.
- `allowCredentials = true`.

Le frontend appelant chaque service sur un port différent (`localhost:8081`, `:8082`, etc.) depuis une page servie sur `localhost:4200`, **chaque requête est cross-origin** par nature : sans cette configuration CORS sur chaque service, le navigateur bloquerait toutes les réponses.

## 5. Côté frontend

- **`TokenInterceptor`** — `frontend/src/app/services/token.interceptor.ts:12-18` : ajoute automatiquement `Authorization: Bearer <token>` à toute requête HTTP sortante si un token existe en `localStorage`, et `:21-25` redirige vers `/login` si une réponse 401 arrive (token expiré/invalide).
- **Guards de route** — `frontend/src/app/services/auth.guard.ts` :
  - `authGuard` (`:6-12`) : bloque l'accès si personne n'est connecté.
  - `sellerGuard` (`:15-25`) : en plus, exige `role === SELLER` (utilisé pour `/seller`, `isSeller()` défini dans `auth.service.ts:28-35`).
  - `clientGuard` (`:28-36`) : exige juste d'être connecté (utilisé pour `/profile/mes-commandes`, ouvert aux deux rôles).
- Le token est stocké en clair dans `localStorage` (pas de cookie httpOnly). C'est un choix simple et courant pour un projet de ce type, mais ça signifie que le token est accessible à n'importe quel script JS qui tournerait sur la page (risque XSS classique) — acceptable ici, à documenter si le projet devait un jour gérer des données sensibles.

## Pour aller plus loin

- [architecture.md](./architecture.md) — vue d'ensemble et parcours complets
- [database.md](./database.md) — schéma de données
