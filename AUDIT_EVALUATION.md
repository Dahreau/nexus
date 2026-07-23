# 📝 README d'Audit : E-Commerce Platform

Ce document justifie techniquement les critères d'audit fonctionnels, de sécurité, de qualité de code et de tests implémentés dans l'architecture microservices de la plateforme.

## 🗄️ 1. Database Design (MongoDB)

**[x] Has the database design been correctly implemented?**
L'architecture de la base de données s'appuie sur Spring Data MongoDB avec une définition claire et stricte des collections pour chaque microservice.

- **Exemple 1 :** Le modèle `Product` définit explicitement sa collection via l'annotation `@Document(collection = "products")`.
- **Exemple 2 :** Le modèle `Order` définit sa collection via `@Document(collection = "orders")`.
- **Exemple 3 :** Le modèle `Cart` gère sa propre collection via `@Document(collection = "carts")`.

**[x] Have the students added new relationships and have they used them correctly?**
En environnement NoSQL (MongoDB), les "relations" se gèrent via l'imbrication de documents (Embedded Documents) pour optimiser les lectures, ou par référencement d'ID pour lier des microservices isolés.

- **Exemple 1 (Imbrication) :** Le document `Cart` embarque directement un tableau d'items via la propriété `private List<CartItem> items;`.
- **Exemple 2 (Imbrication) :** Le document `Order` imbrique l'historique figé des achats via `private List<OrderItem> items;`.
- **Exemple 3 (Référencement) :** Le document `Product` liste les clés étrangères des médias associés via `private List<String> imageIds;`.
- **Exemple 4 (Référencement) :** L'objet `CartItem` conserve les identifiants externes des autres microservices via ses champs `productId` et `sellerId`.

**[x] Did the students convince you with their additions to the database?**
L'intégrité de la donnée est assurée directement au niveau du moteur de base de données.

- **Exemple 1 :** Le champ `userId` du modèle `Cart` possède l'annotation `@Indexed(unique = true)`. Cela garantit au niveau de la BDD qu'un utilisateur ne pourra jamais posséder plus d'un seul panier actif simultanément.

## 🛑 2. Error Handling & Validation

**[x] Are user interactions handled gracefully with appropriate error messages?**
La gestion des erreurs est centralisée côté Backend et interceptée proprement côté Frontend pour guider l'utilisateur.

- **Exemple 1 (Backend - Interception Globale) :** Les classes `GlobalExceptionHandler` attrapent les `RuntimeException` pour renvoyer une réponse HTTP 400 structurée en JSON sous la forme `{ "error": "message" }`.
- **Exemple 2 (Frontend - Affichage Client) :** Le composant `LoginComponent` lit ce JSON et affiche l'erreur en rouge via l'assignation `this.error = err.error?.error || 'Login failed';`.
- **Exemple 3 (Frontend - Affichage Client) :** Le composant `RegisterComponent` utilise la même logique pour les erreurs de création de compte via `this.error = err.error?.error || 'Register failed';`.
- **Exemple 4 (Backend - Validation Jakarta) :** Le DTO `CartRequest` sécurise les entrées avec les annotations `@NotBlank` sur le `productId` et `@Min(value = 1)` sur la `quantity`.

## 🔒 3. Security Measures

**[x] Are security measures consistently applied throughout the application?**
L'application utilise une architecture JWT Stateless stricte pour sécuriser les routes REST et l'interface utilisateur.

- **Exemple 1 (Backend - JWT Filter) :** La classe `JwtAuthFilter` intercepte les requêtes, extrait le token, et hydrate le `SecurityContextHolder` avec le rôle de l'utilisateur (ex: `ROLE_SELLER` ou `ROLE_CLIENT`).
- **Exemple 2 (Backend - Configuration) :** Les classes `SecurityConfig` définissent la stratégie `SessionCreationPolicy.STATELESS` et bloquent toutes les requêtes non authentifiées avec `.anyRequest().authenticated()`, sauf exceptions publiques.
- **Exemple 3 (Frontend - Intercepteur HTTP) :** Le service `TokenInterceptor` injecte automatiquement le header `Authorization: Bearer <token>` dans chaque requête HTTP sortante d'Angular.
- **Exemple 4 (Frontend - Protection des Routes) :** La classe `AuthGuard` bloque l'accès visuel au tableau de bord vendeur si la méthode `this.auth.isSeller()` renvoie `false`.

## 🏗️ 4. Code Quality, Tests & CI/CD

**[x] Are code quality issues identified by SonarQube being addressed and fixed?**
SonarQube est positionné comme un point de blocage strict dans la chaîne d'intégration continue.

- **Exemple 1 :** Dans le `Jenkinsfile`, la Quality Gate est configurée avec `waitForQualityGate(abortPipeline: true)` sur chaque microservice pour faire échouer le pipeline si le code ne respecte pas les normes de qualité.
- **Exemple 2 :** Les DTOs, Controllers et Services respectent des standards de propreté "Enterprise-Grade" (aucun commentaire mort, typage strict).

**[x] Are there unit tests in place for critical parts of the application?**
L'application dispose d'une couverture de tests massive avec JUnit 5 et Mockito.

- **Exemple 1 (Tests Controller) :** La classe `OrderControllerTest` utilise `MockMvc` pour tester la route de validation de panier (`/checkout`) en simulant les tokens JWT et en mockant le `WebClient`.
- **Exemple 2 (Tests Service métier) :** La classe `CartServiceTest` valide la logique métier complexe en simulant le `CartRepository`, vérifiant notamment les levées d'exceptions en cas de "Stock insuffisant".
- **Exemple 3 (Tests de robustesse des Modèles) :** Les modèles et DTOs sont testés unitairement (ex: `DTOTest` pour vérifier la structure de `CartRequest` et `ModelTest` pour les constructeurs de `CartItem`).

**[x] Is the CI/CD pipeline correctly set up and being utilized for PRs?**
Le pipeline DevOps est entièrement automatisé via Jenkins.

- **Exemple 1 :** Le `Jenkinsfile` lance les tests backend en parallèle (`parallel { stage('User Service Test') ... }`).
- **Exemple 2 :** L'orchestration inclut une stratégie de tolérance aux pannes avec un bloc `catch (Exception e)` qui déclenche un Rollback automatique des conteneurs Docker (restauration des images `latest-backup`) en cas de crash lors du déploiement.
- **Exemple 3 :** Des notifications automatiques de succès ou d'échec sont configurées dans le bloc `post`, incluant les liens directs vers les dashboards SonarQube.

**[x] Are developers following a collaborative development process / Are branches merged correctly? / Are code reviews being performed?**
_(Ces critères sont justifiés par l'historique Git, la présence de branches de features comme `feat/cart-service`, et les Pull Requests validées avec revues de code entre développeurs sur le dépôt distant)._
