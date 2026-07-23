# Base de données — buy-02

Ce document décrit le schéma de données de chaque service et explique comment les "relations" fonctionnent alors que chaque service a sa propre base MongoDB isolée.

## 1. Une base MongoDB par service

Chaque service Spring Boot a sa **propre base MongoDB**, avec son propre nom de base, sur la même instance Mongo (un seul conteneur `mongo:6.0`, plusieurs bases logiques dessus) :

| Service | Base | Variable d'env (docker-compose) |
|---|---|---|
| user-service | `userdb` | `SPRING_DATA_MONGODB_URI=mongodb://mongo:27017/userdb` |
| product-service | `productdb` | `SPRING_DATA_MONGODB_URI=mongodb://mongo:27017/productdb` |
| media-service | `mediadb` | `SPRING_DATA_MONGODB_URI=mongodb://mongo:27017/mediadb` |
| order-service | `orderdb` | `SPRING_DATA_MONGODB_URI=mongodb://mongo:27017/orderdb` |
| cart-service | `cartdb` | `SPRING_DATA_MONGODB_URI=mongodb://mongo:27017/cartdb` |

> Note : le fichier `cart-service/src/main/resources/application.yaml` définit un nom de base par défaut différent (`buy01_cart_db`) pour l'exécution locale sans Docker. En pratique, dès que `docker-compose` tourne, la variable d'environnement `SPRING_DATA_MONGODB_URI` a la priorité sur cette valeur par défaut et le service utilise bien `cartdb`, comme les autres. C'est juste un nom de repli local à uniformiser si tu veux nettoyer.

Aucun service n'ouvre de connexion vers la base d'un autre service. Toute donnée dont un service a besoin mais qui appartient à un autre domaine est soit **récupérée par appel HTTP** (voir [architecture.md](./architecture.md#4-comment-les-services-se-parlent-entre-eux)), soit **dupliquée volontairement** au moment où elle est utile (voir section 3).

## 2. Schéma par service

### `userdb.users` (user-service)

| Champ | Type | Note |
|---|---|---|
| `_id` | ObjectId | |
| `name` | String | |
| `email` | String | unique en pratique (vérifié en code, pas d'index unique déclaré) |
| `password` | String | hash BCrypt, jamais le mot de passe en clair |
| `role` | enum `CLIENT` \| `SELLER` | |
| `avatar` | String | présent dans le modèle, pas encore exploité côté frontend |

### `productdb.products` (product-service)

| Champ | Type | Note |
|---|---|---|
| `_id` | ObjectId | |
| `name`, `description` | String | |
| `price` | Double | doit être `> 0` (validé côté contrôleur) |
| `quantity` | Integer | doit être `>= 0`, décrémenté automatiquement quand une commande passe à `PAID` |
| `userId` | String | **référence** vers `userdb.users._id` — c'est le vendeur, aucune contrainte d'intégrité au niveau base (normal en NoSQL / microservices) |
| `sellerName` | String | nom du vendeur **dupliqué** depuis le JWT au moment de la création, pour ne pas avoir à interroger user-service à chaque affichage de la liste produits |
| `imageIds` | List\<String\> | **référence** vers des documents `mediadb.media._id`, ajoutés au fil des uploads |

### `mediadb.media` (media-service)

| Champ | Type | Note |
|---|---|---|
| `_id` | ObjectId | |
| `imagePath` | String | URL publique complète de l'image (`http://.../api/media/file/<uuid>.<ext>`) |
| `productId` | String | **référence** vers `productdb.products._id` |

Les fichiers image eux-mêmes ne sont pas dans MongoDB : ils sont stockés sur disque (dossier `uploads/`, monté en volume Docker), seul le chemin est en base.

### `cartdb.carts` (cart-service)

| Champ | Type | Note |
|---|---|---|
| `_id` | ObjectId | |
| `userId` | String | **`@Indexed(unique = true)`** — un utilisateur ne peut avoir qu'un seul panier, contrainte imposée par un index Mongo, pas juste par la logique applicative |
| `items` | List\<CartItem\> (imbriqué) | |

`CartItem` (sous-document, pas de collection séparée) :

| Champ | Type | Note |
|---|---|---|
| `productId` | String | référence vers `productdb.products._id` |
| `productName` | String | dupliqué depuis product-service au moment de l'ajout au panier |
| `price` | BigDecimal | dupliqué depuis product-service au moment de l'ajout (le prix "vu" par l'utilisateur dans son panier) |
| `quantity` | Integer | |
| `sellerId` | String | dupliqué depuis `product.userId`, utilisé plus tard par order-service pour savoir quel vendeur possède quelle ligne de commande |

### `orderdb.orders` (order-service)

| Champ | Type | Note |
|---|---|---|
| `_id` | ObjectId | |
| `userId` | String | référence vers l'acheteur (`userdb.users._id`) |
| `items` | List\<OrderItem\> (imbriqué) | copie figée du panier au moment du checkout |
| `totalAmount` | BigDecimal | |
| `status` | enum `PENDING`, `PAID`, `SHIPPED`, `DELIVERED`, `CANCELLED` | |
| `paymentMethod` | String | ex. `PAY_ON_DELIVERY` |
| `shippingAddress` | String | |
| `createdAt`, `updatedAt` | LocalDateTime | |

`OrderItem` (sous-document) :

| Champ | Type | Note |
|---|---|---|
| `productId` | String | référence vers `productdb.products._id` |
| `productName` | String | figé au moment du checkout |
| `priceAtPurchase` | BigDecimal | **le prix au moment de l'achat**, volontairement distinct du prix courant du produit (si le vendeur change le prix après coup, l'historique de commande ne doit pas changer) |
| `quantity` | Integer | |
| `sellerId` | String | permet à `order-service` de filtrer "quels articles de cette commande appartiennent à tel vendeur" sans rappeler product-service |

## 3. Comment les "relations" sont gérées sans jointure

En SQL classique on ferait une jointure (`orders JOIN products ON ...`). Ici, comme les données sont dans des bases (et des services) différents, deux techniques sont utilisées ensemble — avec, pour chacune, l'endroit exact du code où ça se passe (ouvre le fichier à la ligne indiquée, ne prends rien ci-dessous pour argent comptant sans vérifier) :

### 1. Référencement par identifiant

Le champ lui-même (juste une `String`, aucun lien technique, aucune jointure possible) :

| Référence | Où dans le code |
|---|---|
| `Product.userId` (pointe vers `userdb.users._id`) | `backend/product-service/src/main/java/com/example/productservice/model/Product.java:16` |
| `Media.productId` (pointe vers `productdb.products._id`) | `backend/media-service/src/main/java/com/example/mediaservice/model/Media.java:11` |
| `CartItem.productId` | `backend/cart-service/src/main/java/com/example/cartservice/model/CartItem.java:16` |
| `OrderItem.productId` | `backend/order-service/src/main/java/com/example/orderservice/model/OrderItem.java:15` |

L'appel HTTP qui va chercher le détail derrière cette référence — exemple complet, le frontend qui récupère les images d'un produit :

1. `frontend/src/app/product-list.component.ts:161-169` — la méthode `fetchImages()` boucle sur les produits affichés et appelle `this.media.byProduct(pid)` pour chacun.
2. `frontend/src/app/services/media.service.ts:13-15` — `byProduct(productId)` fait le vrai appel : `GET http://localhost:8083/api/media/product/${productId}`.
3. `backend/media-service/src/main/java/com/example/mediaservice/controller/MediaController.java:98-100` — `@GetMapping("/product/{productId}")` reçoit la requête et fait `repo.findByProductId(productId)`.

### 2. Dénormalisation (duplication volontaire)

Chaque ligne ci-dessous est l'endroit exact du code où la copie est faite :

| Champ dupliqué | Copié depuis | Où dans le code |
|---|---|---|
| `CartItem.productName` / `price` / `sellerId` | `product-service`, au moment de l'ajout au panier | `backend/cart-service/src/main/java/com/example/cartservice/service/CartService.java:65-71` (bloc `CartItem.builder()` dans `addToCart()`) |
| `OrderItem.productName` / `priceAtPurchase` / `sellerId` | le panier, au moment du checkout | `backend/order-service/src/main/java/com/example/orderservice/service/OrderService.java:284-292` (bloc `OrderItem.builder()` dans `checkout()`) |
| `Product.sellerName` | le JWT, au moment de la création du produit | `backend/product-service/src/main/java/com/example/productservice/controller/ProductController.java:69` (`create()`) |

**Pourquoi `priceAtPurchase` existe** : sans cette copie, l'historique de commande relirait le prix courant du produit à chaque affichage — si le vendeur change son prix après coup, tes vieilles factures changeraient de montant silencieusement. En figeant le prix à `OrderService.java:288` (`.priceAtPurchase(item.getPrice())`, copié depuis le panier), l'historique reste correct pour toujours, même si le produit change ou disparaît ensuite.

**Pourquoi `sellerId` est dupliqué sur chaque `OrderItem`** : une commande peut mélanger des produits de plusieurs vendeurs (le panier n'est pas limité à un seul). Pour qu'un vendeur voie "mes commandes" sans qu'`order-service` rappelle `product-service` à chaque affichage, `sellerId` est filtré directement dans Mongo : `backend/order-service/src/main/java/com/example/orderservice/service/OrderService.java:168` (`searchOrders`, critère `items.sellerId`) et `:226-227` (`updateOrderStatus`, vérifie que le vendeur connecté possède bien un article de la commande).

Le compromis classique de la dénormalisation : ces copies peuvent devenir "périmées". Exemple concret et vérifiable : si un vendeur se renomme, ses produits déjà créés gardent l'ancien `sellerName` — la preuve est dans `backend/product-service/src/main/java/com/example/productservice/controller/ProductController.java:84-86` (`update()`), où `sellerName` n'est réécrit que `if (existing.getSellerName() == null)`. C'est un choix assumé, cohérent avec le principe du prix figé ci-dessus.

## Pour aller plus loin

- [architecture.md](./architecture.md) — qui appelle qui, et les parcours complets
- [security.md](./security.md) — comment les requêtes inter-services sont authentifiées
