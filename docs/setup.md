# Lancer le projet en local

## 1. Prérequis

- Docker + Docker Compose (chemin recommandé, décrit ci-dessous)
- Pour lancer un service hors Docker : Java 17+, Maven, Node 18+, MongoDB local sur `27017`

## 2. Tout lancer avec Docker Compose (recommandé)

**Étape préalable obligatoire (une fois par changement frontend) :** le `Dockerfile` du frontend attend un dossier `frontend/dist/buy-frontend` déjà construit — il ne le construit pas lui-même (voir pourquoi en encadré ci-dessous). Sans ce dossier, `docker compose up --build` échoue sur l'étape `frontend` avec `dist/buy-frontend: not found`.

```bash
cd frontend
npm install
npm run build   # attends bien le message de fin (résumé des tailles de fichiers), ça peut prendre 1-3 min la première fois
cd ..
```

Puis, à la racine :

```bash
JWT_SECRET=un-secret-suffisamment-long INTERNAL_TOKEN=un-autre-secret docker compose up --build
```

> Pourquoi cette étape n'est pas automatisée dans le `Dockerfile` : en CI (Jenkins), l'étape "Build & Test Frontend" du `Jenkinsfile` fait déjà `npm run build` avant d'appeler `docker compose build`, donc `dist/` existe déjà à ce moment-là — le `Dockerfile` n'a jamais eu besoin de le refaire. Ça ne pose problème que si tu lances `docker compose up --build` toi-même, en direct, sans passer par Jenkins (ex. pour un test manuel rapide).

`JWT_SECRET` et `INTERNAL_TOKEN` doivent être définis (le `Jenkinsfile` les injecte via des credentials Jenkins en CI ; en local tu peux les mettre dans un fichier `.env` à la racine — non versionné — ou les passer en ligne de commande comme ci-dessus). Si tu ne les définis pas, chaque service retombe sur une valeur par défaut codée en dur dans son `JwtUtil`/contrôleur — pratique pour un test rapide en solo, à éviter dès que plusieurs personnes travaillent sur le même environnement partagé.

### Ports exposés

| Service | Port | URL locale |
|---|---|---|
| frontend (Angular buildé, servi par nginx) | 4200 | http://localhost:4200 |
| user-service | 8081 | http://localhost:8081/api/auth |
| product-service | 8082 | http://localhost:8082/api/products |
| media-service | 8083 | http://localhost:8083/api/media |
| order-service | 8084 | http://localhost:8084/api/orders |
| cart-service | 8085 | http://localhost:8085/api/carts |
| MongoDB | 27017 | mongodb://localhost:27017 |

Le frontend appelle directement chacun de ces ports (voir [frontend.md](./frontend.md#3-services-un-par-domaine-calqués-sur-les-microservices-backend)) — pas besoin du reverse-proxy nginx (`nginx.conf`, port 443) pour développer en local, il est prévu pour un déploiement derrière un seul nom de domaine.

### Variables d'environnement principales

| Variable | Utilisée par | Rôle |
|---|---|---|
| `JWT_SECRET` | les 5 services backend | Secret partagé de signature/validation des JWT — **doit être identique partout** |
| `INTERNAL_TOKEN` | product, media, order | Secret partagé pour les appels service-à-service (voir [security.md](./security.md#3-jeton-interne-service-à-service)) |
| `SPRING_DATA_MONGODB_URI` | chaque service | URI Mongo (une base par service, voir [database.md](./database.md)) |
| `MEDIA_UPLOAD_DIR` | media-service | Dossier de stockage des fichiers uploadés (`/app/uploads` dans le conteneur, monté en volume sur `./backend/media-service/uploads`) |
| `PRODUCT_SERVICE_URL` | media-service, order-service | Host:port pour joindre product-service (`http://product-service:8082` par défaut, résolu via le réseau Docker) |

## 3. Lancer un service seul, hors Docker (dev rapide sur un seul service)

Possible avec `mvn spring-boot:run` dans `backend/<service>/`, à condition d'avoir MongoDB sur `localhost:27017`. **Limite importante** : les appels inter-services utilisent des noms d'hôte Docker (`http://product-service:8082`, voir [architecture.md](./architecture.md#4-comment-les-services-se-parlent-entre-eux)), qui ne résolvent que dans le réseau `buy-net`. Un service lancé hors Docker qui a besoin d'en appeler un autre échouera sur ces appels, sauf à surcharger l'URL via les variables d'environnement correspondantes (`PRODUCT_SERVICE_URL`, etc.) pour pointer vers `localhost`.

C'est aussi la cause probable si un fichier uploadé "disparaît" après être passé d'un mode d'exécution à l'autre : le dossier `uploads/` relatif change de sens selon le répertoire de travail du process (voir le commentaire dans `MediaController`).

## 4. Frontend seul (`ng serve`)

```bash
cd frontend
npm ci
npm start   # ng serve --open, sur http://localhost:4200
```

`ng serve` a son propre serveur de dev avec fallback SPA intégré (contrairement au nginx du build Docker, qu'on a dû configurer explicitement — voir `frontend/nginx.frontend.conf`). Aucune configuration de proxy n'est nécessaire puisque les services sont appelés en URL absolue.

## 5. CI/CD (Jenkins)

Le `Jenkinsfile` à la racine définit un pipeline en plusieurs étapes :

1. **Checkout** + démarrage de MongoDB seul.
2. **Build & Test Backend** : les 5 services testés **en parallèle** (`mvn clean test`).
3. **SonarQube Analysis** : un scan par service backend + un pour le frontend, chacun avec `waitForQualityGate(abortPipeline: true)` — le pipeline s'arrête si un quality gate échoue.
4. **Build & Test Frontend** : `npm run build`, tests Karma/Jasmine avec couverture de code, puis scan SonarQube du frontend.
5. **Deploy with Rollback Strategy** : rebuild + redéploiement de tous les services via `docker compose`, avec sauvegarde des images `:latest` en `:latest-backup` avant déploiement — en cas d'échec, rollback automatique vers les images précédentes.
6. Un paramètre `ROLLBACK` permet de déclencher un rollback manuel sans rebuild.
7. Notification par email en fin de pipeline (succès/échec), avec liens directs vers les dashboards SonarQube de chaque service.

## Pour aller plus loin

- [architecture.md](./architecture.md)
- [database.md](./database.md)
- [security.md](./security.md)
- [glossary.md](./glossary.md)
