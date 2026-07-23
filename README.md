# buy-02 — E-Commerce Microservices

Plateforme e-commerce composée de 5 microservices Spring Boot + un frontend Angular, communiquant par HTTP/JSON, chacun avec sa propre base MongoDB.

- **user-service** (`:8081`) : inscription, authentification (JWT), rôles
- **product-service** (`:8082`) : CRUD produits (vendeurs), recherche/filtrage, stock
- **media-service** (`:8083`) : upload et service des images produit
- **order-service** (`:8084`) : panier → commande, suivi de statut, historique, statistiques
- **cart-service** (`:8085`) : panier de chaque utilisateur

## 📚 Documentation

Le détail technique est dans [`docs/`](./docs) :

| Doc | Contenu |
|---|---|
| [docs/architecture.md](./docs/architecture.md) | **À lire en premier.** Carte des services, qui appelle qui, parcours complets (checkout, upload d'image, changement de statut...) |
| [docs/database.md](./docs/database.md) | Schéma MongoDB de chaque service, comment les relations fonctionnent sans jointure |
| [docs/security.md](./docs/security.md) | JWT, rôles, CORS, jeton interne service-à-service |
| [docs/frontend.md](./docs/frontend.md) | Structure de l'application Angular, routing, services, guards |
| [docs/setup.md](./docs/setup.md) | Lancer le projet en local, variables d'environnement, CI/CD Jenkins |
| [docs/glossary.md](./docs/glossary.md) | Définitions des notions techniques employées dans le code (pour qui découvre) |
| [docs/audit.md](./docs/audit.md) | Vérification détaillée de chaque critère de la grille d'audit, avec preuves dans le code |
| [docs/code-review-prep.md](./docs/code-review-prep.md) | Cheat sheet pour se préparer à un code review (questions probables + réponses courtes) |

Voir aussi [`AUDIT_EVALUATION.md`](./AUDIT_EVALUATION.md), [`TEST_PLAN.md`](./TEST_PLAN.md) et [`REPORT_TESTING.md`](./REPORT_TESTING.md) pour le détail des critères d'audit et des tests — ces documents datent d'une étape antérieure du projet (avant order-service/cart-service) et se concentrent sur les 3 premiers services ; `docs/` ci-dessus reflète l'état actuel des 5 services.

## 🚀 CI/CD Pipeline (Jenkins)

Pipeline complet : build + tests en parallèle des 5 services backend, scan SonarQube par service (+ frontend) avec quality gate bloquant, build/tests frontend (Karma/Jasmine + Puppeteer headless), puis déploiement Docker Compose avec stratégie de rollback automatique en cas d'échec (sauvegarde des images `:latest` avant chaque déploiement). Détail complet dans [docs/setup.md](./docs/setup.md#5-cicd-jenkins).

## Démarrage rapide

```bash
JWT_SECRET=change-me INTERNAL_TOKEN=change-me-too docker compose up --build
```

Frontend sur http://localhost:4200, services backend sur les ports 8081 à 8085. Détail des prérequis, variables d'environnement et lancement d'un service isolé (hors Docker) : [docs/setup.md](./docs/setup.md).
