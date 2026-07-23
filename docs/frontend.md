# Frontend — buy-02

Application Angular 16 classique (NgModule, pas de standalone components, pas de lazy loading). Un seul module racine (`AppModule`) déclare tous les composants.

## 1. Routing

Défini dans `app.module.ts` :

| Route | Composant | Garde |
|---|---|---|
| `/` | `ProductListComponent` | aucune (public) |
| `/login` | `LoginComponent` | aucune |
| `/register` | `RegisterComponent` | aucune |
| `/seller` | `SellerDashboardComponent` | `authGuard` + `sellerGuard` |
| `/profile/mes-commandes` | `ClientDashboardComponent` | `authGuard` + `clientGuard` |

Le panier (`CartComponent`) n'est **pas une route** : c'est un panneau latéral (`app-cart`) monté une fois pour toutes dans `app.component.html`, ouvert/fermé via `toggleCart()` (appelé depuis la navbar ou depuis `ProductListComponent`).

## 2. Composants principaux

```
AppComponent (navbar + <router-outlet> + <app-cart>)
├── ProductListComponent        (page d'accueil : liste + recherche + ajout panier)
├── LoginComponent / RegisterComponent
├── SellerDashboardComponent    (formulaire produit + upload image + liste produits + commandes reçues)
├── ClientDashboardComponent    (stats client + historique de ses commandes)
├── CartComponent               (panneau panier, toujours monté)
└── MediaManagerComponent       (déclaré dans AppModule mais non utilisé dans aucun template — code mort)
```

## 3. Services (un par domaine, calqués sur les microservices backend)

| Service Angular | Backend visé | Base URL |
|---|---|---|
| `AuthService` | user-service | `http://localhost:8081/api/auth` |
| `ProductService` | product-service | `http://localhost:8082/api/products` |
| `MediaService` | media-service | `http://localhost:8083/api/media` |
| `OrderService` | order-service | `http://localhost:8084/api/orders` |
| `CartService` | cart-service | `http://localhost:8085/api/carts` |

**Important : ces URLs sont volontairement absolues** (pas de chemin relatif type `/api/orders`). Le frontend n'utilise pas de proxy Angular CLI (`proxy.conf.json` n'existe pas dans ce projet) : sans host explicite, une requête relative part vers `localhost:4200` (le serveur du frontend lui-même), qui ne sait évidemment pas répondre à `/api/orders`. C'est la cause de plusieurs bugs corrigés pendant le développement (checkout, upload d'image, historique de commandes qui ne se chargeait pas) — si tu ajoutes un nouvel appel HTTP, garde toujours une URL absolue vers le bon port de service.

## 4. Authentification côté client

- `AuthService.getToken()/setToken()` : lit/écrit le JWT dans `localStorage`.
- `AuthService.isSeller()` / `getUserId()` / `getUserName()` : décodent le payload du JWT (base64, sans vérifier la signature — la vérification de signature est faite côté backend, le frontend fait juste confiance à ce qu'il a stocké) pour afficher/adapter l'UI selon le rôle.
- `TokenInterceptor` : ajoute l'en-tête `Authorization` à chaque requête sortante et gère la déconnexion automatique sur 401.
- Guards de route : voir [security.md](./security.md#5-côté-frontend).

## 5. Gestion d'état

Pas de state management global (pas de NgRx/Akita). Chaque composant garde son propre état local (`products: any[]`, `cart: Cart | null`, etc.) et le recharge depuis le backend après chaque action qui le modifie (ex. `addToCart()` recharge le panier via `AppComponent.loadCart()`). C'est volontairement simple, adapté à la taille du projet — à garder en tête si tu ajoutes une fonctionnalité qui a besoin d'un état partagé entre composants non liés.

## 6. Styles

Bootstrap 5 (CDN, avec intégrité SRI) pour la structure générale, plus des styles maison dans `src/styles/` organisés par composant (`components/cart.css`, `components/cards.css`, `components/forms.css`...) et `utilities/responsive.css` pour les media queries mobile/tablette.

## Pour aller plus loin

- [architecture.md](./architecture.md) — vue d'ensemble microservices
- [security.md](./security.md) — détail de l'authentification
- [setup.md](./setup.md) — lancer le frontend en local
