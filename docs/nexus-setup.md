# Setup Nexus — Artifact Management System

Documentation de setup/configuration/usage pour l'audit "Artifact Management System with Nexus". Calquée sur la grille d'audit (`docs/nexus-audit.md`). À remplir au fur et à mesure — chaque section liste ce qu'il faut capturer en écran.

> Statut : 🚧 en cours

## 1. Setup Nexus Repository Manager

- Installation : Nexus tourne en conteneur Docker (`sonatype/nexus3`) déclaré dans `docker-compose.yml`, service `nexus` (port `8086` pour l'UI/API, `5001` pour le repo Docker hosted).
- Utilisateur non-root : l'image officielle `sonatype/nexus3` fait tourner le process sous un utilisateur non-root par défaut à l'intérieur du conteneur — pas de configuration manuelle nécessaire côté OS.
- Repos créés :
  - [ ] `maven-releases` (hosted)
  - [ ] `maven-snapshots` (hosted)
  - [ ] `maven-central` (proxy)
  - [ ] `maven-public` (group)
  - [ ] `docker` (hosted)

**Captures à prendre :** `docker compose up -d nexus` + logs de démarrage réussi · page de création du premier utilisateur admin · liste des repos créés.

## 2. Sample Web Application

buy-02 : 5 microservices Spring Boot (`user-service`, `product-service`, `media-service`, `order-service`, `cart-service`), chacun un projet Maven indépendant. Voir `README.md` / `docs/architecture.md` pour le détail.

## 3. Artifact Publishing

`distributionManagement` ajouté à chaque `pom.xml` pointant vers `nexus-releases`/`nexus-snapshots`. Commande : `mvn clean deploy`.

**Captures à prendre :** extrait du `pom.xml` avec le bloc `distributionManagement` · sortie console d'un `mvn deploy` réussi · l'artefact visible dans l'UI Nexus.

## 4. Dependency Management (proxy)

`~/.m2/settings.xml` (ou settings.xml de projet en CI) configuré avec un mirror `<mirrorOf>*</mirrorOf>` vers `maven-public`, forçant toutes les dépendances à transiter par Nexus.

**Captures à prendre :** `settings.xml` (credentials masqués) · `mvn clean install -X` montrant que les artefacts viennent de Nexus et non d'Apache/Central directement.

## 5. Versioning

`maven-releases` configuré en mode immuable (redeploy désactivé). Numéro de version géré dans chaque `pom.xml`.

**Captures à prendre :** option "Disable redeploy" activée sur `maven-releases` · deux versions différentes d'un même artefact visibles dans Nexus · démonstration d'un rollback (changement de version dans le pom + build résolu depuis Nexus).

## 6. Docker Integration

Repo `docker` (hosted) sur le connecteur `5001`. Docker Bearer Token Realm activé (Security → Realms). `insecure-registries` configuré côté client Docker si HTTP.

**Captures à prendre :** config du repo Docker dans Nexus · activation du realm · `docker login`/`push` réussi · image visible dans le repo Nexus.

## 7. Continuous Integration (CI)

Pipeline Jenkins (`Jenkinsfile`) étendu avec un stage de publication (Maven `deploy` + `docker push`) déclenché à chaque changement poussé, en plus des stages build/test existants. Credentials Nexus centralisés via un credential Jenkins dédié (`nexus-creds`), injecté en variables d'env (`withCredentials`) — jamais en clair dans le repo.

**Captures à prendre :** credential `nexus-creds` créé dans Jenkins · pipeline avec le nouveau stage de publication · run réussi de bout en bout.

## 8. Documentation

Ce document + `docs/nexus-notions.md` (notions internes).

## 9. Bonus — Sécurité et contrôle d'accès

- [ ] Rôles/utilisateurs Nexus au-delà de l'admin (ex: rôle lecture seule pour la CI, rôle déploiement)
- [ ] Permissions au niveau repo (qui peut push vs pull)
- [ ] Restriction d'accès à certains repos/artefacts

**Captures à prendre :** création d'un rôle · attribution de permissions par repo · test d'accès refusé pour un utilisateur non autorisé.
