# Cheat sheet — préparation code review

L'idée de ce document : les questions qu'on peut raisonnablement te poser en review, avec une réponse courte à connaître par cœur + où pointer dans le code pour appuyer la réponse. La nouveauté principale de ce projet par rapport aux précédents, c'est le passage à **plusieurs microservices qui communiquent entre eux** (avant, chaque service était probablement isolé) — donc c'est la partie la plus creusée ici, mais tout le reste est couvert aussi.

## 1. "Explique-moi l'architecture en 30 secondes"

> "5 microservices Spring Boot indépendants (user, product, media, order, cart), chacun avec sa propre base MongoDB, chacun sur son port. Le frontend Angular appelle chaque service directement. Il n'y a pas de gateway applicatif : l'authentification est décentralisée via JWT, signé par user-service, vérifié indépendamment par chaque service avec le même secret partagé."

Fichier à avoir en tête : `docs/architecture.md` section 2 (carte des services).

## 2. "Comment les microservices communiquent-ils entre eux ?"

Toujours en **HTTP synchrone**, jamais de file de messages (pas de Kafka/RabbitMQ ici). Deux clients HTTP utilisés selon les services : `WebClient` (réactif, mais utilisé avec `.block()` donc synchrone dans les faits) et `RestTemplate` (dans `media-service`).

4 appels concrets à connaître par cœur :

| Qui appelle | Qui répond | Pourquoi | Fichier |
|---|---|---|---|
| cart-service | product-service | vérifier stock/prix/vendeur avant d'ajouter au panier | `CartService.fetchProductDetails()` — `backend/cart-service/src/main/java/com/example/cartservice/service/CartService.java:106` |
| order-service | cart-service | récupérer puis vider le panier au checkout | `OrderService.checkout()` — `backend/order-service/src/main/java/com/example/orderservice/service/OrderService.java:276` |
| order-service | product-service | décrémenter le stock quand une commande passe à `PAID` | `OrderService.updateOrderStatus()` (`OrderService.java:236-245`, `orderProducer.sendStockUpdate(...)`) → `ProductController.updateStock()` — `backend/product-service/src/main/java/com/example/productservice/controller/ProductController.java:100-101` |
| media-service | product-service | rattacher une image uploadée au produit (`imageIds`) | `MediaController.upload()` — `backend/media-service/src/main/java/com/example/mediaservice/controller/MediaController.java:43-70` (appel ligne 86) → `ProductController.addImage()` — `ProductController.java:123-126` |

**Si on te demande "et si product-service est down pendant ce moment-là ?"** : sois honnête — `CartService.fetchProductDetails()` (`CartService.java:106`) fait échouer l'ajout au panier proprement (exception catchée, message clair). Le call `order-service → product-service` pour le stock est, lui, volontairement non-bloquant : si ça échoue, on logue un warning mais la commande passe quand même à `PAID` (`OrderService.java:236-245`, `try/catch` autour de `sendStockUpdate`). C'est un choix assumé : le paiement ne doit pas être bloqué par un souci de synchronisation de stock.

## 3. "Comment les services se trouvent-ils sur le réseau ?"

Deux mondes différents à bien distinguer :
- **Entre conteneurs Docker** (service → service) : nom du service Compose comme host, ex. `http://product-service:8082`, résolu par le réseau `buy-net`.
- **Depuis le navigateur** (frontend → service) : chaque service est appelé en `http://localhost:808x` directement, il n'y a pas de proxy Angular (pas de `proxy.conf.json`). C'est volontaire et cohérent dans tout le frontend — **si on te demande pourquoi pas de gateway unique**, la réponse honnête : ça simplifie le projet pour sa taille, le compromis est que le frontend doit connaître le port de chaque service.

## 4. "Comment gérez-vous les relations en base sans jointure ?"

C'est LA question à anticiper. Réponse structurée en deux temps :

1. **Référencement par ID** : un document garde juste l'identifiant d'un document d'un autre service (ex. `Product.userId` pointe vers un utilisateur, sans contrainte de clé étrangère possible puisque ce sont deux bases différentes).
2. **Dénormalisation volontaire** : certains champs sont copiés au bon moment plutôt que d'être relus à chaque fois. Exemple à donner en premier (le plus parlant) : `OrderItem.priceAtPurchase` — le prix est **figé** au moment du checkout, pour que l'historique de commande ne change jamais même si le vendeur modifie son prix après coup. Ce n'est pas un oubli de ne pas avoir juste stocké `productId`, c'est un choix pour l'intégrité des données historiques.

Détail complet avec tous les champs : `docs/database.md`.

**Question piège possible : "et si les deux données divergent (ex. le vendeur change son nom) ?"** Réponse honnête : `Product.sellerName` ne se resynchronise que si le champ était vide au moment d'une modification du produit — `backend/product-service/src/main/java/com/example/productservice/controller/ProductController.java:84` (`if (existing.getSellerName() == null)`, dans `update()`) — c'est une limite connue et assumée, pas un bug caché.

## 5. "Comment fonctionne l'authentification à travers 5 services différents ?"

- `user-service` est le seul avec des mots de passe (BCrypt).
- Il émet un JWT (HS256) avec 3 infos : `sub` (id), `role`, `name`.
- Les 4 autres services **ne recontactent jamais user-service** : chacun a son propre filtre (`JwtAuthFilter`) qui valide la signature avec le même secret (`JWT_SECRET`, variable d'env identique partout).
- Conséquence à savoir dire clairement : **si `JWT_SECRET` diffère ne serait-ce que sur un service, ce service rejette silencieusement tous les tokens** (utilisateur traité comme non connecté). C'est le genre de detail qui montre que t'as compris le mécanisme, pas juste copié-collé le code.

Deuxième mécanisme à distinguer du JWT : le **jeton interne** (`X-Internal-Token` / `INTERNAL_TOKEN`), utilisé uniquement pour les 2-3 appels service-à-service listés en section 2, jamais vu par un utilisateur. Détail : `docs/security.md`.

## 6. "Un vendeur peut-il aussi acheter ?"

Oui, volontairement : rien dans l'énoncé n'exclut un vendeur de l'achat. Un vendeur ne peut juste pas acheter **son propre** produit — `backend/cart-service/src/main/java/com/example/cartservice/service/CartService.java:42-44` (`addToCart()`, `if (userId.equals(product.getUserId())) throw ...`). C'est un choix de design qu'on a fait nous-mêmes en cours de route (pas explicitement demandé), à assumer si on te le demande.

## 7. "Comment le frontend est structuré ?"

Un seul `NgModule`, pas de lazy loading (taille du projet ne le justifie pas). Un service Angular par domaine (`ProductService`, `CartService`, `OrderService`, `MediaService`, `AuthService`), chacun avec une URL absolue vers le bon port du bon microservice backend — **volontairement pas d'URL relative** : sans hôte explicite, une requête part vers le frontend lui-même (`localhost:4200`), pas vers le service voulu. C'est la cause de plusieurs bugs qu'on a eus et corrigés en cours de projet (checkout, upload d'image, historique de commandes) — bon exemple à citer si on te demande "avez-vous eu des difficultés techniques ?".

## 8. "Comment est gérée la sécurité côté frontend ?"

`TokenInterceptor` (`frontend/src/app/services/token.interceptor.ts:12-18`) ajoute automatiquement le JWT à chaque requête sortante et déconnecte proprement sur un 401 (`:21-25`). Trois guards de route dans `frontend/src/app/services/auth.guard.ts` : `authGuard` (`:6-12`, connecté), `sellerGuard` (`:15-25`, connecté + vendeur), `clientGuard` (`:28-36`, connecté, les deux rôles — utilisé pour "Mes commandes", accessible aux vendeurs aussi).

## 9. "Parlez-moi de vos tests"

Réponse honnête et assumée (à dire tel quel plutôt qu'à minimiser) :

> "On a priorisé la couverture sur `order-service` et `cart-service` (5 fichiers de test chacun : controller, service, DTO, modèle, gestion d'erreurs) parce que c'est là que se joue la logique la plus risquée — paiement, stock, panier. Les autres services backend ont des tests de contrôleur plus légers. Côté frontend, la couverture est faible (1 fichier de test), c'est le point le plus faible du projet, assumé plutôt que caché."

Ne pas prétendre à une couverture complète si on creuse — le pipeline Jenkins calcule une vraie couverture de code (SonarQube), donc un chiffre existe et peut être montré directement depuis le dashboard SonarQube plutôt que discuté dans l'abstrait.

## 10. "Comment fonctionne votre CI/CD ?"

Pipeline Jenkins (`Jenkinsfile`, à la racine) en 5 étapes : build+test parallèle des 5 services → SonarQube (un scan par service + un pour le frontend, chacun avec un *quality gate* qui peut bloquer le pipeline) → build+tests frontend avec couverture → déploiement Docker Compose → **rollback automatique** si le déploiement échoue (les images `:latest` précédentes sont taguées `:latest-backup` avant chaque déploiement et restaurées en cas d'erreur). Notification email en fin de pipeline avec les liens SonarQube.

Bon point à mentionner spontanément si on te demande "qu'est-ce qui est le plus abouti dans ce projet côté best practices" : le rollback automatique est au-delà de ce qui est généralement attendu à ce niveau.

## 11. Questions "pièges" à anticiper, avec des réponses honnêtes prêtes

- **"Pourquoi pas d'API Gateway ?"** → Pas nécessaire pour la taille du projet ; le frontend appelle chaque service directement, ce qui reste simple à raisonner tant qu'il n'y a que 5 services.
- **"Que se passe-t-il si deux services ont des données incohérentes (ex. panier référence un produit supprimé) ?"** → Pas de vérification de cohérence a posteriori (pas de saga/compensation). C'est une limite connue d'un système "eventually inconsistent" simplifié : à assumer plutôt qu'à prétendre que c'est géré.
- **"Pourquoi MongoDB et pas SQL ?"** → Chaque service ayant son propre schéma indépendant, un document store convient bien au pattern *database-per-service* — pas besoin de migrations de schéma partagées ni de jointures inter-services de toute façon impossibles.
- **"Le wishlist / plusieurs moyens de paiement (bonus) ?"** → Non implémentés, assumé (c'était optionnel).

## Pour aller plus loin

- [docs/audit.md](./audit.md) — vérification détaillée de chaque critère d'audit avec preuves
- [architecture.md](./architecture.md), [database.md](./database.md), [security.md](./security.md), [frontend.md](./frontend.md), [setup.md](./setup.md), [glossary.md](./glossary.md)
