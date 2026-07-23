# Architecture — buy-02

Ce document explique comment les services de buy-02 sont découpés, comment ils communiquent entre eux, et comment une requête traverse le système de bout en bout. C'est le document à lire en premier si tu découvres le projet.

## 1. Vue d'ensemble

buy-02 est une plateforme e-commerce en **microservices** : chaque domaine métier (utilisateurs, produits, médias, panier, commandes) est un service Spring Boot indépendant, avec sa propre base MongoDB, déployé comme un conteneur Docker séparé. Le frontend est une application Angular unique qui parle directement à chacun de ces services.

```
                        ┌─────────────────────┐
                        │   Angular (4200)     │
                        │  (frontend, nginx)   │
                        └──────────┬───────────┘
                                   │ appels HTTP directs, un par service
        ┌───────────┬─────────────┼─────────────┬────────────┐
        ▼            ▼             ▼             ▼            ▼
  ┌───────────┐ ┌───────────┐ ┌───────────┐ ┌───────────┐ ┌───────────┐
  │  user-svc │ │product-svc│ │ media-svc │ │ order-svc │ │ cart-svc  │
  │   :8081   │ │   :8082   │ │   :8083   │ │   :8084   │ │   :8085   │
  └─────┬─────┘ └─────┬─────┘ └─────┬─────┘ └─────┬─────┘ └─────┬─────┘
        │             │             │             │             │
        ▼             ▼             ▼             ▼             ▼
    userdb        productdb      mediadb       orderdb        cartdb
                              (MongoDB, une base par service)
```

Il n'y a pas d'API Gateway applicatif : le frontend appelle chaque service directement sur son port (`http://localhost:808x/...`). Un nginx en frontal (`nginx.conf`, port 443) existe pour un déploiement "propre" derrière un seul nom de domaine, mais en local le plus simple est d'appeler les services sur leurs ports exposés par Docker (voir [setup.md](./setup.md)).

## 2. Carte des services

| Service | Port | Base Mongo | Rôle |
|---|---|---|---|
| `user-service` | 8081 | `userdb` | Inscription, connexion, émission des JWT |
| `product-service` | 8082 | `productdb` | CRUD produits (vendeur), recherche/filtrage, stock |
| `media-service` | 8083 | `mediadb` | Upload et service des images produit |
| `order-service` | 8084 | `orderdb` | Commandes : checkout, suivi de statut, historique, stats |
| `cart-service` | 8085 | `cartdb` | Panier de chaque utilisateur |

Chaque service est un module Maven indépendant sous `backend/<service>/`, avec son propre `Dockerfile`, son propre `pom.xml`, et sa propre configuration Spring Security. Rien n'est partagé en code entre les services (pas de librairie commune) : ils communiquent uniquement par HTTP.

## 3. Pourquoi une base par service (et pas une base partagée) ?

C'est le principe central du pattern *database-per-service* : chaque service est seul propriétaire de ses données et de son schéma. Aucun autre service n'a de connexion directe à une base qui n'est pas la sienne. Ça évite qu'un service casse le schéma d'un autre, et ça permet de faire évoluer/scaler chaque service indépendamment.

La conséquence directe : **on ne peut pas faire de jointure SQL entre "produits" et "commandes"**, par exemple, puisqu'ils vivent dans des bases (et des services) différents. Voir [database.md](./database.md) pour le détail de comment les relations sont quand même gérées (référencement d'ID + duplication volontaire de certains champs).

## 4. Comment les services se parlent entre eux

Toute communication inter-service est **synchrone, en HTTP/JSON**, via `WebClient` (réactif, non-bloquant côté appelant mais utilisé ici avec `.block()`) ou `RestTemplate`. Il n'y a **pas de file de messages** (pas de Kafka/RabbitMQ) dans ce projet : chaque appel est un aller-retour HTTP direct.

Quand un service appelle un autre service **depuis l'intérieur de Docker**, il utilise le nom du service Docker Compose comme host (`http://product-service:8082/...`), résolu par le réseau interne `buy-net`. Ça ne marche que si les deux services tournent dans le même `docker-compose` — un service lancé en local hors Docker ne pourra pas résoudre ces noms (voir [setup.md](./setup.md)).

Appels inter-services existants :

| Appelant | Appelé | Pourquoi |
|---|---|---|
| `cart-service` | `product-service` (`GET /api/products/{id}`) | Vérifier que le produit existe, récupérer son prix/stock/vendeur avant de l'ajouter au panier — `backend/cart-service/src/main/java/com/example/cartservice/service/CartService.java:106` (`fetchProductDetails()`, appelée depuis `addToCart()` ligne 35) |
| `order-service` | `cart-service` (`GET /api/carts`, `DELETE /api/carts/clear`) | Récupérer le contenu du panier au moment du checkout, puis le vider une fois la commande créée — `backend/order-service/src/main/java/com/example/orderservice/service/OrderService.java:276` (`checkout()`) |
| `order-service` | `product-service` (`POST /api/products/stock-update`) | Décrémenter le stock quand une commande passe de `PENDING` à `PAID` — `OrderService.java:236-245` (`updateOrderStatus()`, `orderProducer.sendStockUpdate(...)` par article) |
| `media-service` | `product-service` (`POST /api/products/{id}/images`) | Prévenir product-service qu'une image a été rattachée à un produit, pour mettre à jour `imageIds` — appel dans `backend/media-service/src/main/java/com/example/mediaservice/controller/MediaController.java:86`, reçu par `backend/product-service/src/main/java/com/example/productservice/controller/ProductController.java:123-126` (`addImage()`) |

Ces trois derniers appels (stock-update, images) sont protégés par un **jeton interne** (`X-Internal-Token`), différent du JWT utilisateur — voir [security.md](./security.md#jeton-interne-service-à-service).

Le frontend, lui, n'a jamais besoin d'appeler un service "à travers" un autre : il appelle chaque service directement (ex. il affiche les images d'un produit en interrogeant `media-service` lui-même, pas via `product-service`).

## 5. Authentification distribuée

Il n'y a pas de service d'autorisation central consulté à chaque requête. `user-service` est le seul à connaître les mots de passe et à émettre des JWT (signés en HS256). Tous les autres services reçoivent ce JWT dans l'en-tête `Authorization: Bearer <token>` et le **valident eux-mêmes**, indépendamment, car ils partagent le même secret de signature (`JWT_SECRET`, une variable d'environnement identique injectée à tous les services). C'est ce qui permet à chaque service de vérifier "qui fait la requête" et "avec quel rôle" sans jamais interroger user-service. Détails dans [security.md](./security.md).

## 6. Parcours complets (pour comprendre "qui fait quoi")

### Inscription / connexion
1. Le frontend appelle `POST /api/auth/register` (ou `/login`) sur `user-service` — `backend/user-service/src/main/java/com/example/userservice/controller/AuthController.java`.
2. `user-service` hash le mot de passe (BCrypt, `AuthController.java:40`), crée/vérifie l'utilisateur dans `userdb`, puis génère un JWT (`AuthController.java:43`/`:59`) contenant `sub` (id utilisateur), `role` (`CLIENT`/`SELLER`) et `name`.
3. Le frontend stocke ce token dans `localStorage` (`frontend/src/app/services/auth.service.ts:17-19`, `setToken()`) et l'attache à chaque requête suivante via un intercepteur HTTP (`TokenInterceptor`, `frontend/src/app/services/token.interceptor.ts:12-18`).

### Un vendeur crée un produit avec une image
1. `POST /api/products` sur `product-service`, avec le JWT — `backend/product-service/src/main/java/com/example/productservice/controller/ProductController.java:58-72` (`create()`). Le service vérifie le rôle `SELLER` (`:60`, `validateSeller()`), valide prix/quantité (`:61`), crée le document dans `productdb` en copiant `sellerName` depuis le JWT (`:69`).
2. Le frontend reçoit le produit créé (avec son `id`), puis envoie l'image en `multipart/form-data` vers `POST /api/media/upload` sur `media-service` — `frontend/src/app/seller-dashboard.component.ts:95-101` (upload déclenché après `saveProduct()`).
3. `media-service` stocke le fichier sur disque, crée un document dans `mediadb` référençant `productId` (`backend/media-service/src/main/java/com/example/mediaservice/controller/MediaController.java:43-70`, `upload()`), puis notifie `product-service` (`MediaController.java:86`, `POST /api/products/{id}/images` avec le jeton interne) pour que le produit garde la liste de ses `imageIds` (reçu côté `ProductController.java:123-126`, `addImage()`).

### Un client ajoute un produit au panier
1. `POST /api/carts` sur `cart-service` — `backend/cart-service/src/main/java/com/example/cartservice/controller/CartController.java`.
2. `cart-service` appelle `product-service` pour vérifier que le produit existe et a du stock (`CartService.java:35`, `fetchProductDetails()`), refuse si le vendeur essaie d'acheter son propre produit (`CartService.java:42-44`), puis enregistre/actualise le document `Cart` de l'utilisateur dans `cartdb` (`CartService.java:74`, `cartRepository.save(cart)`).

### Checkout
1. `POST /api/orders/checkout` sur `order-service` — `backend/order-service/src/main/java/com/example/orderservice/service/OrderService.java:276` (`checkout()`).
2. `order-service` appelle `cart-service` pour récupérer le panier actuel, fige chaque ligne (nom, prix, vendeur) dans une nouvelle commande `orderdb` avec le statut `PENDING` (`OrderService.java:284-292`, bloc `OrderItem.builder()`), puis appelle `cart-service` pour vider le panier.

### Le vendeur marque une commande "Payée"
1. `PUT /api/orders/{id}/status?status=PAID` sur `order-service` — `backend/order-service/src/main/java/com/example/orderservice/controller/OrderController.java:104-110` (`updateOrderStatus()`).
2. `order-service` vérifie que le vendeur possède bien au moins un article dans cette commande (`OrderService.java:229-234`, `isSellerOfOrder`), met à jour le statut, puis appelle `product-service` (`/stock-update`) pour chaque article afin de décrémenter le stock réel (`OrderService.java:236-245`, `orderProducer.sendStockUpdate(...)`).

## 7. Les deux nginx du projet

Il y a deux configurations nginx distinctes, à ne pas confondre :

- **`frontend/nginx.frontend.conf`** : nginx *à l'intérieur* du conteneur `frontend`, qui sert les fichiers statiques Angular sur le port 4200 et fait le *fallback SPA* (`try_files ... /index.html`) pour que les routes Angular (ex. `/seller`) fonctionnent après un F5.
- **`nginx.conf`** (racine) : un reverse-proxy optionnel en façade sur le port 443 (HTTPS), qui route `/api/auth`, `/api/products`, `/api/media` vers les bons services et renvoie le reste vers le conteneur frontend. Il ne route pas encore `/api/orders` ni `/api/carts` — le projet fonctionne en local sans lui puisque le frontend appelle directement les ports des services (voir [setup.md](./setup.md)).

## Pour aller plus loin

- [database.md](./database.md) — schéma de données de chaque service et comment les relations sont gérées sans jointure
- [security.md](./security.md) — JWT, rôles, CORS, jeton interne
- [frontend.md](./frontend.md) — structure de l'application Angular
- [setup.md](./setup.md) — faire tourner le projet en local
- [glossary.md](./glossary.md) — définitions des notions techniques utilisées dans le code
