# Notions Nexus — doc de travail

Doc perso (pas pour l'audit) pour comprendre ce qu'on met en place et pourquoi, au fur et à mesure. On complète au fil du projet.

## C'est quoi Nexus, concrètement

Nexus Repository Manager = un serveur qui stocke et sert des **artefacts** (fichiers produits par un build : `.jar`, `.war`, images Docker...), à la place de dépendre uniquement de dépôts publics (Maven Central, Docker Hub). Deux rôles principaux dans ce projet :

1. **Dépôt de sortie** : là où buy-02 publie ses propres `.jar`/images après un build (`mvn deploy`, `docker push`).
2. **Proxy/cache** : Nexus se met entre Maven et Maven Central. Maven demande une dépendance à Nexus, Nexus la récupère une fois sur Central puis la garde en cache pour toutes les demandes suivantes (plus rapide, fonctionne même si Central est down).

## Les types de repos qu'on va créer

- **Hosted** : le repo appartient à Nexus, personne d'autre ne l'héberge. C'est là qu'on pousse nos propres artefacts (nos `.jar` buy-02, nos images Docker).
  - `maven-releases` : versions figées (ex: `1.0.0`). Nexus peut être configuré en "Disable redeploy" dessus → une fois publiée, une version ne peut plus être écrasée. Ça garantit que ce qui tourne en prod = exactement ce qui a été testé.
  - `maven-snapshots` : versions en dev (suffixe `-SNAPSHOT` dans le pom.xml), ré-déployables librement.
  - `docker` (hosted) : nos images Docker à nous.
- **Proxy** : Nexus fait l'intermédiaire vers un dépôt externe (Maven Central) et cache ce qui transite.
- **Group** : un repo "virtuel" qui agrège plusieurs repos (hosted + proxy) sous une seule URL. C'est ce qu'on donne à Maven comme unique point d'entrée (`maven-public`), pour qu'il n'ait pas à choisir lui-même où chercher.

## `distributionManagement` (pom.xml)

Bloc qui dit à Maven *où* envoyer l'artefact quand on fait `mvn deploy` (par opposition à `mvn install` qui reste en local). On y met l'URL du repo Nexus correspondant (`nexus-snapshots` ou `nexus-releases` selon la version du pom).

## `settings.xml` (credentials Maven)

Le pom.xml ne contient jamais de mot de passe. Les identifiants Nexus vivent dans `~/.m2/settings.xml` (en local) ou dans un `settings.xml` versionné utilisant des variables d'env (`${env.NEXUS_USER}`) injectées par Jenkins en CI — jamais en clair dans le repo.

## Pourquoi l'utilisateur `nexus` (pas root)

Exigence de sécurité classique : un service exposé sur le réseau ne doit pas tourner avec les droits root, sinon une faille dans Nexus = accès root sur toute la machine. Avec l'image Docker officielle `sonatype/nexus3`, c'est déjà le cas par défaut (le process tourne sous un user non-root dans le conteneur) — un des arguments pour partir sur Docker plutôt qu'une install WSL manuelle.

## Docker Bearer Token Realm

Docker ne parle pas HTTP Basic Auth nativement pour l'auth registre — il utilise un système de token (Bearer). Nexus a un "realm" dédié à activer (Security → Realms) pour que `docker login`/`push`/`pull` fonctionnent contre son repo Docker hosted.

## `insecure-registries` (Docker daemon)

Docker refuse par défaut de parler à un registre en HTTP (il veut du HTTPS avec certificat valide). En dev/local, on n'a pas de certificat → il faut explicitly whitelister l'adresse du registre Nexus dans `/etc/docker/daemon.json` sous `insecure-registries`.

## Versioning côté Nexus

Deux mécanismes complémentaires :
- Numéro de version dans le `pom.xml` (`1.0.0`, `1.1.0`...) — décidé par nous.
- `maven-releases` en mode immuable → chaque version publiée reste consultable indéfiniment, ce qui permet un rollback : redéployer une ancienne version = juste demander à Maven de résoudre cette version-là plutôt que la dernière.

## `localhost` ne veut pas dire la même chose partout

Piège classique avec Jenkins en conteneur : `localhost:8086` (Maven) et `localhost:5001` (Docker) fonctionnent très bien depuis WSL, mais pas de la même façon depuis Jenkins.

- **Maven** (`mvn deploy`) tourne comme process natif *dans* le conteneur Jenkins → son `localhost` désigne le conteneur Jenkins lui-même, pas Nexus. Il faut viser le nom du service Docker sur le réseau `buy-net` : `http://nexus:8081` (port interne du conteneur, pas le port mappé `8086`). D'où `settings-ci.xml` (séparé du `~/.m2/settings.xml` local) + le flag `-DaltDeploymentRepository=...` dans le `Jenkinsfile`, qui force cette URL au lieu de celle du `pom.xml`.
- **Docker** (`docker build`/`push`) n'a pas ce problème : `docker-compose.yml` monte `/var/run/docker.sock` dans Jenkins, donc les commandes `docker` de Jenkins sont exécutées par le daemon Docker de **l'hôte** (pattern "Docker outside of Docker"), pas dans le conteneur Jenkins. `localhost:5001` y fonctionne donc normalement, comme en test manuel.

## Credentials Jenkins → Nexus

`nexus-creds` (Username/password) créé dans Jenkins, injecté via `withCredentials(...)` en variables d'env temporaires (`NEXUS_USER`/`NEXUS_PASS`) le temps du stage. Le mot de passe n'est jamais écrit en clair dans le `Jenkinsfile`, le `pom.xml`, ou un fichier versionné — seul `settings-ci.xml` référence `${env.NEXUS_USER}`/`${env.NEXUS_PASS}`, des placeholders résolus au runtime par Maven à partir des variables d'environnement.

---
*(section à compléter au fur et à mesure)*
