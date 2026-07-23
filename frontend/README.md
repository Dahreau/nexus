Angular Frontend (minimal scaffold)

This folder contains a minimal Angular app scaffold (app modules and components) to integrate with the backend microservices.

Notes:
- This is a light scaffold. To run it locally you should use Angular CLI to create a full project and copy the files in `src/app` into the generated project, or run `npm install` to install dependencies listed in package.json and then `ng serve`.
- The frontend expects the backend services to run on the following ports:
  - user-service: http://localhost:8081
  - product-service: http://localhost:8082
  - media-service: http://localhost:8083

Recommended quick setup (if you have node/npm):
- Install Angular CLI if you don't have it: `npm install -g @angular/cli`
- Create a new angular project and copy the `src/app` files into the generated project's `src/app`
  - `npx @angular/cli new buy-frontend --routing --style=css`
  - copy files from this scaffold into `buy-frontend/src/app`
  - `cd buy-frontend` and `ng serve` to run dev server

Quick run using the scaffold in this folder
-----------------------------------------
If you want to use the scaffolded Angular project in this repository (uses local dependencies):

```powershell
cd frontend
npm install
npx ng serve --open
```

This will run the Angular dev server on http://localhost:4200 and should interact with the backend services running on the ports listed above.

Components included here:
- AuthService (handles login/register and stores JWT in localStorage)
- ProductService (CRUD calls for products)
- MediaService (upload images with size/type validation on client)
- LoginComponent, RegisterComponent, SellerDashboardComponent, ProductListComponent, MediaManagerComponent

This scaffold aims to provide a clear starting point you can drop into an Angular CLI project.
