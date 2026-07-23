# Audit report — buy-02

Ce document reprend **chaque ligne de la grille d'audit officielle**, from scratch, sans tenir compte des cases déjà cochées avant cette relecture. Pour chaque point : le verdict, et surtout **la preuve concrète dans le code** (fichier, méthode, extrait) qui le justifie — pour que tu puisses pointer exactement la même chose en code review.

**Méthode utilisée pour vérifier** (transparence, pour que tu saches ce qui a été réellement testé vs relu) :
- Relecture manuelle complète des 5 services backend et du frontend.
- Le frontend a été **compilé pour de vrai** dans un environnement isolé : `tsc --noEmit` sur `tsconfig.app.json` et `tsconfig.spec.json` → **0 erreur de type**, sur les deux.
- Le backend (Java 17 / Maven) n'a pas pu être compilé depuis cet environnement (Maven et JDK 17 non installables ici, pas d'accès root). La vérification backend est donc une relecture manuelle rigoureuse, pas une preuve de compilation automatisée — **je recommande de lancer `mvn clean verify` sur chaque service avant l'audit réel**, pour avoir cette confirmation.
- Je n'ai pas pu faire tourner l'application complète (Docker + MongoDB) ni cliquer dans l'UI depuis cet environnement : les parcours utilisateurs ont été vérifiés en traçant le code de bout en bout (frontend → backend → base), pas en les exécutant réellement. Voir la section 8 pour un plan de test manuel avant l'audit.
- L'historique Git (`git log`) a été inspecté en local : 84 commits sur `main`, 11 Pull Requests fusionnées. Je n'ai pas pu accéder à l'API GitHub depuis cet environnement (réseau restreint) pour vérifier les commentaires/approbations de review — à vérifier toi-même dans l'onglet "Files changed" / "Reviewers" de chaque PR sur GitHub.

---

## Functional

### ✅ Has the database design been correctly implemented?

Oui. Chaque microservice a sa propre base MongoDB (pattern *database-per-service*), avec des collections explicitement déclarées :

- `Product` → `@Document(collection = "products")` — `backend/product-service/src/main/java/com/example/productservice/model/Product.java:8`
- `Order` → `@Document(collection = "orders")` — `backend/order-service/src/main/java/com/example/orderservice/model/Order.java:17`
- `Cart` → `@Document(collection = "carts")` — `backend/cart-service/src/main/java/com/example/cartservice/model/Cart.java:19`
- `Media` → `@Document(collection = "media")` — `backend/media-service/src/main/java/com/example/mediaservice/model/Media.java:6`
- `User` → `@Document(collection = "users")` — `backend/user-service/src/main/java/com/example/userservice/model/User.java:6`

Chaque champ a un type cohérent avec son usage (`BigDecimal`/`Double` pour les montants, `enum` pour les statuts/rôles, `LocalDateTime` pour les dates). Détail complet dans [database.md](./database.md).

### ✅ Have the students added new relationships and have they used them correctly ?

Deux techniques utilisées, correctement, aux bons endroits :

1. **Référencement par ID** entre services : `Product.userId` → vendeur, `Media.productId` → produit, `CartItem.productId`/`OrderItem.productId` → produit, `OrderItem.sellerId` → vendeur.
2. **Documents imbriqués** : `Cart.items: List<CartItem>`, `Order.items: List<OrderItem>` — pas de collection séparée, cohérent avec le fait qu'un item de panier/commande n'a aucun sens hors de son panier/commande parent.
3. **Dénormalisation volontaire et justifiée** : `OrderItem.priceAtPurchase` fige le prix au moment de l'achat — `backend/order-service/src/main/java/com/example/orderservice/service/OrderService.java:291` (`.priceAtPurchase(item.getPrice())` dans `checkout()`), pour que l'historique de commande ne bouge jamais même si le vendeur change son prix après coup. `Product.sellerName` est copié depuis le JWT à la création — `backend/product-service/src/main/java/com/example/productservice/controller/ProductController.java:69` (`p.setSellerName(getAuthenticatedName())` dans `create()`) pour afficher le nom du vendeur sans appel réseau supplémentaire à chaque affichage de la liste produits.

Ce ne sont pas des relations ajoutées au hasard : chacune correspond à un besoin fonctionnel réel du projet (voir [database.md](./database.md#3-comment-les-relations-sont-gérées-sans-jointure) pour le détail).

### ✅ Did the students convince you with their additions to the database ?

Trois exemples concrets qui montrent une vraie réflexion, pas juste "ça marche" :

- `Cart.userId` porte `@Indexed(unique = true)` — `backend/cart-service/src/main/java/com/example/cartservice/model/Cart.java:25` : la règle "un utilisateur = un seul panier actif" est imposée **par MongoDB lui-même**, pas seulement par la logique applicative — même un bug côté service ne pourrait pas créer deux paniers pour le même utilisateur.
- `OrderItem.sellerId` est dupliqué exprès pour permettre à `order-service` de répondre à `GET /api/orders/seller` sans jamais interroger `product-service` : le filtre Mongo `items.sellerId` est appliqué directement dans `OrderService.searchOrders()` — `backend/order-service/src/main/java/com/example/orderservice/service/OrderService.java:171` (`Criteria.where(ITEMS_SELLER_ID_FIELD).is(sellerId)`) — chaque service reste indépendant même pour une requête qui "traverse" plusieurs domaines métier.
- Le prix figé (`priceAtPurchase`, `OrderService.java:291`) résout un vrai problème d'intégrité temporelle qu'un design naïf (juste référencer `productId` et relire le prix courant) aurait introduit silencieusement.

### ⚠️ Are developers following a collaborative development process with PRs and code reviews ?

**Partiellement vérifiable depuis cet environnement.** Ce qui est confirmé :

- 11 Pull Requests mergées sur `main` (`git log --merges` → `Merge pull request #1` à `#11`), chacune depuis une branche nommée par fonctionnalité : `feat/database-design`, `feat/product-service`, `feat/cart-service`, `feat/cart-service.1`, `feat/front-cart`, `feat/checkout`, `test/momo`, `feat/documentation`, `fix/frontend-test-coverage`.
- Convention de nommage de commits cohérente (`feat:`, `fix:`, `test:`) sur plusieurs branches.

Ce que je **n'ai pas pu vérifier** depuis cet environnement (accès réseau restreint, pas d'accès à l'API GitHub) : si chaque PR a effectivement reçu un commentaire/une approbation de review avant merge. **Statut : accepté tel quel par le développeur** — risque connu, pas de vérification GitHub supplémentaire demandée à ce stade. Si tu changes d'avis avant l'audit, ça reste vérifiable en 2 minutes : onglet "Conversation" de 2-3 PRs sur GitHub.

### ⚠️ Are the implemented functionalities consistent with the project instructions ?

Repris fonctionnalité par fonctionnalité par rapport à l'énoncé :

**Orders MicroService**
- Suivi de statut : ✅ `PENDING → PAID → SHIPPED → DELIVERED`, plus `CANCELLED`, avec transitions contrôlées côté serveur — `backend/order-service/src/main/java/com/example/orderservice/service/OrderService.java:225` (`updateOrderStatus`, vérifie `isSellerOfOrder` avant tout changement) et affichées comme badge + menu déroulant côté vendeur — `frontend/src/app/seller-dashboard.component.html:199-212`.
- Liste des commandes, utilisateur ET vendeur, avec recherche : ✅ `OrderController.getMyOrders` (`backend/order-service/src/main/java/com/example/orderservice/controller/OrderController.java:64`) / `getSellerOrders` (`OrderController.java:80`), tous deux délèguent à `OrderService.searchOrders` (`OrderService.java:162`) qui accepte `status`, `start`, `end`, `keyword`, pagination. Le frontend expose mot-clé + filtre statut côté client — `frontend/src/app/client-dashboard.component.html:37-53`.
- Remove / cancel / redo : ✅ les trois sont branchés côté UI — `cancelOrder` (`frontend/src/app/client-dashboard.component.ts:70`), `redoOrder` (`:82`), `deleteOrder` (`:94`) — avec les règles métier associées côté serveur : annulation seulement si `PENDING` (`OrderService.java:201`), suppression seulement si `DELIVERED`/`CANCELLED` (`OrderService.java:218`).

**User Profile / Seller Profile**
- Client : dépenses totales, nombre de commandes, produit le plus acheté — `UserStatsDTO`, agrégation Mongo dans `OrderService.getUserStats()` (`backend/order-service/src/main/java/com/example/orderservice/service/OrderService.java:65`), affiché dans `frontend/src/app/client-dashboard.component.html:9-31`.
- Vendeur : chiffre d'affaires, commandes livrées, meilleur produit vendu — `SellerStatsDTO`, `OrderService.getSellerStats()` (`OrderService.java:110`), affiché dans `frontend/src/app/seller-dashboard.component.html:132-154`.
- Nuance : les deux DTO retournent un **top 5** (`topProducts`/`bestSellers`), mais l'UI n'affiche que le premier élément ("produit favori"/"meilleur vendeur") — `client-dashboard.component.html:28` (`topProducts[0]`), `seller-dashboard.component.html:151` (`bestSellers[0]`). La donnée est là, l'exploitation UI est minimale — pas un manque au sens strict de l'énoncé, mais peu impressionnant visuellement si on te le demande en review.

**Search and Filtering**
- ✅ Recherche par mot-clé + fourchette de prix, paginée — `ProductController.searchProducts()` (`backend/product-service/src/main/java/com/example/productservice/controller/ProductController.java:205`) délègue à `ProductRepository.searchAndFilter()` (`ProductRepository.java:20`), avec formulaire correspondant sur la page produits — `frontend/src/app/product-list.component.ts:24-40` (template inline).

**Shopping Cart**
- ✅ Ajout, modification de quantité, suppression, vidage — persistés côté serveur (voir case dédiée plus bas).
- ✅ "Pay on delivery" : `paymentMethod` envoyé tel quel (`"PAY_ON_DELIVERY"`, en dur) — `frontend/src/app/cart.component.ts:64`, stocké sur la commande.
- ✅ Un vendeur ne peut pas acheter son propre produit — `backend/cart-service/src/main/java/com/example/cartservice/service/CartService.java:42-44` (comparaison `userId.equals(product.getUserId())` dans `addToCart()`, throw si vrai).

**Verdict global** : cohérent avec l'énoncé sur le fond. Le point "⚠️" plutôt que "✅" tient à deux nuances mineures (top 5 sous-exploité côté UI, et à la case suivante sur la propreté/absence d'erreurs — voir juste en dessous) plutôt qu'à une fonctionnalité manquante.

### ⚠️ Are the implemented functionalities clean and do they not pop up any errors or warnings in both back and front end ?

**Sur ce qui a été trouvé et corrigé pendant le développement** (donc plus des bugs actuels, mais à connaître si on te pose la question "avez-vous eu des bugs, comment les avez-vous trouvés/corrigés") : checkout qui échouait (mauvaise URL), upload d'image qui échouait après création produit (mauvaise URL), changement de statut de commande en échec (endpoint de mise à jour de stock manquant côté product-service), liste de produits vendeur toujours vide (variable jamais réassignée), panier très lent à l'ouverture (`backdrop-filter: blur` sur toute la page), 404 nginx sur F5 (pas de fallback SPA, corrigé par `<base href="/">` dans `frontend/src/index.html:5`), hash SRI de Bootstrap Icons corrompu (`index.html:26`), navbar mobile invisible sans bouton pour l'ouvrir (`frontend/src/app/app.component.html:6-8`), erreur JS après ajout au panier (`Cannot read properties of undefined (reading 'loadCart')` — `ProductListComponent` essayait de récupérer une référence à `AppComponent`, son ancêtre, via `@ViewChild`, ce qui ne fonctionne qu'avec des enfants directs du template ; corrigé avec un `Subject` partagé dans `CartService` — `frontend/src/app/services/cart.service.ts:20-27` — que `CartComponent` écoute pour se recharger, `frontend/src/app/cart.component.ts:22`), requête de recherche invalide envoyée pendant la frappe quand `min > max` (`ProductController.searchProducts` renvoie 400, visible en rouge dans la console réseau ; corrigé en amont côté frontend, `frontend/src/app/product-list.component.ts:151-154`, la requête n'est simplement plus envoyée tant que la plage n'est pas valide) — tous corrigés et vérifiés par relecture (voir l'historique de cette conversation pour le détail technique de chaque correctif).

**Ce qui reste, honnêtement** :
- Le frontend compile sans erreur TypeScript (vérifié automatiquement, voir en tête de document).
- Le backend n'a pas pu être recompilé automatiquement ici — relu ligne à ligne mais **lance `mvn clean verify` toi-même** sur les 5 services avant l'audit pour avoir la garantie zéro warning de compilation.
- Aucun test end-to-end réel n'a pu être exécuté depuis cet environnement (pas de Docker/Mongo disponibles ici) — voir section "Run a full test" plus bas pour un plan de vérification manuelle avant l'audit.

Je marque ce point ⚠️ non pas parce qu'un problème est identifié, mais parce que je ne peux pas honnêtement cocher "aucune erreur" sans l'avoir vu tourner — à toi de faire ce dernier tour manuel (15-20 minutes, voir section 8) pour pouvoir cocher en toute confiance.

### ✅ Add products to the shopping cart and refresh the page — are they still there with the right quantities ?

Oui, par construction : le panier n'est **jamais** stocké côté client (pas de `localStorage`/state Angular persistant). `CartComponent.ngOnInit()` (`frontend/src/app/cart.component.ts:20-22`) appelle `loadCart()` (`:24-29`) qui fait `GET /api/carts`, lu depuis MongoDB via `CartService.getCartByUserId()` (`backend/cart-service/src/main/java/com/example/cartservice/service/CartService.java:27-30`). Un F5 recharge toute l'application Angular, ce qui redéclenche cet appel — le panier revient tel qu'il était en base, quantités incluses. Pas de mécanisme de cache local qui pourrait le désynchroniser.

### ✅ Are code quality issues identified by SonarQube being addressed and fixed ?

Oui — corrections faites sur des remontées SonarQube réelles (règles citées) pendant cette session : 8 problèmes de contraste texte/fond (`css:S7924`) dans `cart.css`, `cards.css`, `forms.css`, `styles.css` et le template du dashboard vendeur, corrigés avec des couleurs recalculées pour respecter le ratio WCAG AA (≥ 4.5:1) au lieu de juste changer la couleur au hasard. Un problème d'intégrité de ressource (`Web:S5725`) sur Google Fonts, documenté comme non-applicable (le CSS de Google Fonts varie par navigateur, un hash SRI y casserait le chargement pour certains utilisateurs) plutôt que "corrigé" à l'aveugle.

**Pour l'audit** : le critère demande aussi de "documenter les améliorations faites suite au feedback SonarQube" — ce document + l'historique de conversation en sont la trace, mais ce serait plus solide d'avoir un court paragraphe dédié dans le repo (voir suggestion en fin de document).

### ⚠️ Does the application provide a seamless and responsive user experience ?

Des media queries existent (`responsive.css`, `cart.css`) à 480px et 768px, couvrant le panier et les grilles de produits/commandes. Bootstrap fournit la structure générale (navbar, grille). Pas de media query dédiée pour le formulaire de création produit au-delà de ce qui est hérité. **Je n'ai pas pu tester sur un vrai mobile/tablette depuis cet environnement** — à faire toi-même (DevTools en mode responsive suffit) sur `/`, `/seller` et `/profile/mes-commandes` avant l'audit.

### ✅ Are user interactions handled gracefully with appropriate error messages ?

Oui, de façon assez systématique : validations prix/quantité (backend `ProductController.validatePriceAndQuantity()` — `backend/product-service/src/main/java/com/example/productservice/controller/ProductController.java:149`, appelée depuis `create()`/`update()` aux lignes 61 et 77 — + frontend `min`/`required` sur les champs du formulaire, `frontend/src/app/seller-dashboard.component.html:47-53`), messages d'erreur backend remontés dans les `alert()` frontend plutôt que des messages génériques (ex. `saveProduct()` — `frontend/src/app/seller-dashboard.component.ts:119-122` — affiche `err.error.error` s'il existe), erreurs de validation `@Valid` renvoyées avec un message par champ plutôt qu'un dump technique brut (`GlobalExceptionHandler.handleValidationException()` — `backend/cart-service/src/main/java/com/example/cartservice/config/GlobalExceptionHandler.java:16` et l'équivalent `backend/order-service/src/main/java/com/example/orderservice/config/GlobalExceptionHandler.java:16`). Chaque guard de route redirige proprement (`/login`) plutôt que de laisser une page cassée — `frontend/src/app/services/auth.guard.ts` (les trois guards `authGuard`/`sellerGuard`/`clientGuard` font tous `router.navigate(['/login'])` si pas de token).

### ✅ Are security measures consistently applied throughout the application ?

Oui, de façon uniforme sur les 5 services :
- Mots de passe hashés BCrypt (`user-service`, jamais en clair).
- JWT (HS256) signé par un secret partagé (`JWT_SECRET`), vérifié indépendamment par chaque service (`JwtAuthFilter` × 5, même pattern).
- Autorisation par rôle au niveau contrôleur (`ROLE_SELLER` vérifié avant toute mutation de produit/commande).
- CORS configuré à l'identique sur les 5 services (origines `localhost`/`127.0.0.1`, credentials autorisés).
- Appels inter-services sensibles protégés par un jeton interne distinct du JWT utilisateur (`X-Internal-Token`, comparé à `INTERNAL_TOKEN`).

Détail complet avec citations dans [security.md](./security.md).

---

## Collaboration and Development Process

### ⚠️ Are code reviews being performed for each PR ?

Voir la case équivalente plus haut — historique de PRs confirmé (11 mergées), contenu des reviews non vérifiable depuis cet environnement. À confirmer sur GitHub directement.

### ✅ Is the CI/CD pipeline correctly set up and being utilized for PRs ?

Le `Jenkinsfile` définit un pipeline cohérent et complet : checkout → démarrage MongoDB → build/test **en parallèle** des 5 services backend (`mvn clean test`) → scan SonarQube par service + frontend, chacun avec `waitForQualityGate(abortPipeline: true)` (le pipeline s'arrête si la qualité n'est pas au niveau) → build/tests frontend (Karma/Jasmine + couverture, Puppeteer headless) → déploiement Docker Compose avec sauvegarde des images précédentes et **rollback automatique** en cas d'échec de déploiement → notification email succès/échec avec liens directs vers les dashboards SonarQube. C'est un pipeline nettement au-dessus du minimum attendu pour un projet de ce niveau. Le déclenchement effectif "sur chaque PR" (webhook GitHub → Jenkins) se configure côté interface Jenkins, pas dans le `Jenkinsfile` lui-même — à confirmer que ce déclencheur est bien actif si on te le demande.

### ✅ Are branches merged correctly, and is the main codebase up-to-date ?

Oui : 84 commits sur `main`, tous arrivés via des merges de PR proprement nommées (`git log --merges` propre, pas de merge sauvage détecté). Toutes les branches feature visibles ont été mergées dans `main`.

### ⚠️ Does the application pass a comprehensive test to ensure that all new features work as expected ?

C'est le point que je ne peux pas cocher à ta place : je n'ai pas pu lancer l'application complète (Docker + MongoDB + les 5 services + le frontend) depuis cet environnement pour cliquer dedans réellement. Tout ce qui précède est vérifié par lecture de code de bout en bout (je sais que chaque endpoint appelé par le frontend existe côté backend, avec les bons champs, les bonnes règles), et par compilation réelle côté frontend — mais ce n'est pas la même preuve qu'un clic réel dans l'UI. **Voir le plan de test manuel section 8** : suis-le une fois avant l'audit, ça prend 15-20 minutes et ça referme ce point.

### ✅ Are there unit tests in place for critical parts of the application ?

Oui, de façon inégale mais réelle :

| Service | Fichiers de test |
|---|---|
| `order-service` | 5 (controller, service, DTO, model, exception handler) |
| `cart-service` | 5 (idem) |
| `product-service` | 2 (controller + contexte Spring) |
| `media-service` | 2 (idem) |
| `user-service` | 2 (idem) |
| `frontend` | 1 (`login.component.spec.ts`) |

Les deux services au cœur de la logique métier la plus risquée (paiement/commande, panier) sont les mieux couverts — un choix de priorisation défendable si on te le demande en review, plutôt qu'une couverture uniforme superficielle. Le point faible assumé est le frontend (1 seul fichier) — voir le [docs/code-review-prep.md](./code-review-prep.md) pour comment le présenter en review.

---

## Bonus

### ❌ Is the wishlist feature functioning as expected ?

Non implémenté. Aucune trace de wishlist dans le modèle de données ni le frontend. Pas un problème (c'est un bonus), juste à ne pas revendiquer si on te le demande.

### ❌ Are the implemented payment methods functioning correctly ?

Un seul mode de paiement existe : `"PAY_ON_DELIVERY"`, en chaîne fixe côté frontend (`cart.component.ts`), stocké tel quel sur `Order.paymentMethod`. Aucun choix multi-méthodes côté UI. Le bonus "different payment methods" n'a pas été tenté.

---

## Synthèse

| Section | ✅ | ⚠️ (à vérifier toi-même) | ❌ |
|---|---|---|---|
| Functional | 8 | 3 | 0 |
| Collaboration | 2 | 2 | 0 |
| Bonus | 0 | 0 | 2 |

Rien de ⚠️ n'indique un problème identifié dans le code — dans tous les cas, c'est soit "je n'ai pas pu le vérifier depuis cet environnement" (GitHub, exécution réelle), soit une nuance mineure (top 5 sous-exploité, responsive pas testé sur vrai device). Le plan section 8 ferme les points vérifiables par toi en 20-30 minutes.

## 8. Plan de vérification manuelle avant l'audit (à faire toi-même)

1. `docker compose up --build`, attendre que les 5 services + Mongo soient up.
2. Créer un compte `SELLER`, créer un produit avec image, vérifier qu'elle s'affiche.
3. Créer un compte `CLIENT` (ou se déconnecter/reconnecter), ajouter ce produit au panier, F5, vérifier qu'il est toujours là.
4. Checkout → vérifier la commande dans "Mes commandes", avec le bon statut et le détail des articles.
5. Repasser en `SELLER`, changer le statut de la commande `PENDING → PAID`, vérifier que le stock du produit a diminué.
6. Tester la recherche produits (mot-clé + prix min/max).
7. Tester `/seller` et `/profile/mes-commandes` en F5 direct (vérifie le fix nginx SPA).
8. Ouvrir les DevTools (onglet Console + Network) pendant tout ça, vérifier qu'il n'y a aucune erreur rouge.
9. Réduire la fenêtre / DevTools responsive sur `/`, `/seller`, `/profile/mes-commandes`.
10. Sur GitHub : ouvrir 2-3 PRs au hasard, confirmer qu'il y a une review/un commentaire avant le merge.

## Pour aller plus loin

- [docs/code-review-prep.md](./code-review-prep.md) — cheat sheet pour le code review lui-même
- [architecture.md](./architecture.md), [database.md](./database.md), [security.md](./security.md), [frontend.md](./frontend.md), [setup.md](./setup.md), [glossary.md](./glossary.md)
