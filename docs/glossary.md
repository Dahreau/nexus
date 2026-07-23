# Glossaire des notions utilisées dans le code

Ce document explique, en partant de zéro, les notions techniques qu'on croise dans le code de buy-02. L'idée : un dev qui connaît la programmation mais découvre ces outils/patterns précis doit pouvoir comprendre le "pourquoi" sans aller chercher ailleurs.

## Architecture

**Microservice** — une application indépendante, responsable d'un seul domaine métier (ici : utilisateurs, produits, médias, panier, commandes), déployée et exécutée séparément des autres. S'oppose à une architecture "monolithique" où tout serait dans une seule application. Avantage : chaque service peut évoluer, planter ou être redéployé sans affecter les autres. Inconvénient : il faut gérer la communication réseau entre services, et on perd les jointures de base de données classiques (voir [database.md](./database.md)).

**API Gateway** — un point d'entrée unique qui route les requêtes vers le bon microservice. Ce projet n'en a pas au sens strict (pas de service dédié à ça) : le frontend appelle chaque microservice directement. Le `nginx.conf` à la racine joue un rôle proche (reverse-proxy qui route par chemin d'URL) mais n'est pas branché sur tous les services — voir [architecture.md](./architecture.md#7-les-deux-nginx-du-projet).

**Database-per-service** — le pattern qui dit que chaque microservice a sa propre base de données, jamais partagée. Voir [database.md](./database.md).

**Dénormalisation** — copier volontairement une donnée d'un service dans un document d'un autre service (ex. le nom d'un produit recopié dans une ligne de panier), pour éviter un appel réseau à chaque lecture. Le prix à payer : la copie peut devenir périmée si l'original change. Utilisé partout dans ce projet — voir [database.md](./database.md#3-comment-les-relations-sont-gérées-sans-jointure).

## Backend (Spring Boot)

**DTO (Data Transfer Object)** — une classe simple qui ne sert qu'à transporter des données entre couches ou entre services (ex. `ProductDTO` côté cart-service, qui ne reprend que les champs de `Product` utiles au panier). Différent d'une entité de base de données (`@Document`) : un DTO n'est jamais persisté tel quel.

**Repository (Spring Data)** — une interface (ex. `ProductRepository extends MongoRepository<Product, String>`) qui donne accès aux opérations de base de données (`findAll`, `findById`, `save`...) sans écrire de requête. Spring génère l'implémentation automatiquement. Les requêtes personnalisées (ex. la recherche par mot-clé/prix) s'écrivent avec l'annotation `@Query`.

**`@Document`** — annotation Spring Data MongoDB qui marque une classe comme correspondant à une collection MongoDB (ex. `@Document(collection = "products")`). L'équivalent MongoDB de `@Entity` en JPA/SQL.

**Filter chain (Spring Security)** — avant qu'une requête HTTP n'atteigne un contrôleur, elle traverse une chaîne de filtres. Ce projet ajoute un filtre maison, `JwtAuthFilter`, qui lit et valide le JWT pour peupler le contexte de sécurité (`SecurityContextHolder`) — voir [security.md](./security.md).

**WebClient / RestTemplate** — deux façons de faire des appels HTTP sortants depuis un service Spring vers un autre. `RestTemplate` est l'ancienne API, synchrone et simple. `WebClient` est l'API réactive plus récente ; dans ce projet elle est utilisée avec `.block()`, ce qui la rend synchrone dans les faits (choix pragmatique, pas de vrai traitement réactif ici).

**BCrypt** — algorithme de hachage de mot de passe conçu pour être volontairement lent (donc coûteux à attaquer par force brute), avec un "sel" intégré automatiquement. Utilisé via `BCryptPasswordEncoder` dans `user-service` — jamais de mot de passe en clair stocké.

## Communication réseau

**JWT (JSON Web Token)** — un jeton signé contenant des informations (ici : id utilisateur, rôle, nom) que le serveur peut vérifier sans avoir besoin de les stocker en base ni de recontacter le service qui l'a émis. Voir [security.md](./security.md#1-authentification--jwt-signé-vérifié-partout).

**CORS (Cross-Origin Resource Sharing)** — mécanisme du navigateur qui bloque par défaut les requêtes JavaScript vers un domaine/port différent de celui de la page. Un service doit explicitement "autoriser" les origines qui ont le droit de l'appeler (`CorsConfigurationSource`). Sans ça, le frontend sur `:4200` ne pourrait appeler aucun service sur `:808x`.

**Preflight / requête OPTIONS** — avant certaines requêtes cross-origin (avec un en-tête personnalisé comme `Authorization`), le navigateur envoie automatiquement une requête `OPTIONS` pour vérifier que le serveur autorise l'appel réel. Chaque `JwtAuthFilter` de ce projet répond `200 OK` immédiatement sur `OPTIONS`, sans essayer de valider un token (il n'y en a pas sur un preflight).

**SPA (Single Page Application) / fallback SPA** — une application web qui charge une seule page HTML puis gère la navigation en JavaScript (Angular Router ici), sans recharger depuis le serveur à chaque changement d'URL. Problème : si on tape ou rafraîchit directement `/seller`, le serveur ne connaît pas ce chemin en tant que fichier réel. Le "fallback SPA" (`try_files $uri $uri/ /index.html`) dit au serveur web de toujours renvoyer `index.html` si le fichier demandé n'existe pas, pour laisser Angular gérer la route côté client.

**SRI (Subresource Integrity)** — un hash (ex. `integrity="sha384-..."`) mis sur une balise `<script>`/`<link>` chargeant une ressource externe (CDN), pour que le navigateur refuse de l'exécuter si le contenu reçu ne correspond pas exactement à ce hash. Protège contre un CDN compromis qui servirait un fichier modifié. Ne fonctionne bien que sur des ressources statiques et stables (mauvaise idée sur une ressource qui varie selon le visiteur, comme Google Fonts).

## Frontend (Angular)

**Module (`NgModule`)** — un regroupement de composants/services/imports. Ce projet a un seul module racine (`AppModule`), pas de découpage en modules par fonctionnalité.

**Composant (`Component`)** — un bloc d'UI réutilisable avec son propre template HTML/CSS et sa logique TypeScript (ex. `CartComponent`).

**Service (Angular, `@Injectable`)** — une classe qui porte de la logique partagée (le plus souvent des appels HTTP), injectée dans les composants qui en ont besoin (ex. `ProductService`). À ne pas confondre avec un "microservice" backend — même mot, deux échelles différentes.

**Guard** — une fonction qui décide si la navigation vers une route est autorisée (ex. `authGuard`). Retourne `true`/`false`, ou redirige.

**Interceptor (`HttpInterceptor`)** — intercepte toutes les requêtes HTTP sortantes (ou réponses entrantes) pour leur appliquer un traitement commun. Ici, `TokenInterceptor` ajoute le JWT à chaque requête et gère les 401.

**Observable (RxJS)** — le mécanisme utilisé par `HttpClient` pour représenter une réponse asynchrone. On s'y abonne avec `.subscribe({ next, error })`. Une requête HTTP Angular ne part réellement que quand on s'y abonne.

## Infra / Outils

**Docker / conteneur** — un environnement isolé et reproductible qui empaquette une application avec tout ce dont elle a besoin pour tourner. Chaque service de ce projet a son `Dockerfile`.

**Docker Compose** — un outil pour décrire et lancer plusieurs conteneurs ensemble (`docker-compose.yml`), avec leur réseau, leurs variables d'environnement, leurs volumes.

**Réseau Docker (`buy-net`)** — un réseau virtuel où les conteneurs peuvent se joindre par leur nom de service (ex. `http://product-service:8082`) au lieu d'une IP. Ne fonctionne qu'entre conteneurs du même réseau.

**Volume** — un dossier partagé entre l'hôte et un conteneur (ou persistant entre redémarrages), pour ne pas perdre de données quand un conteneur est recréé (ex. les fichiers uploadés dans media-service, ou les données MongoDB).

**Reverse proxy** — un serveur (ici nginx) placé devant une ou plusieurs applications, qui redirige chaque requête entrante vers la bonne application selon des règles (chemin d'URL, domaine...). Voir [architecture.md](./architecture.md#7-les-deux-nginx-du-projet).

**CI/CD (Jenkins)** — l'automatisation qui build, teste et déploie le projet à chaque changement. Voir [setup.md](./setup.md#5-cicd-jenkins).

**SonarQube / Quality Gate** — un outil d'analyse statique de code qui détecte bugs potentiels, failles de sécurité et mauvaises pratiques. Un "quality gate" est un ensemble de seuils (ex. couverture de tests minimale, zéro bug bloquant) : si le code ne les atteint pas, le pipeline CI peut être configuré pour s'arrêter (`waitForQualityGate(abortPipeline: true)` dans ce projet).

## Pour aller plus loin

- [architecture.md](./architecture.md)
- [database.md](./database.md)
- [security.md](./security.md)
- [frontend.md](./frontend.md)
- [setup.md](./setup.md)
