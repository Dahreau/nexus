import { NgModule } from '@angular/core';
import { BrowserModule } from '@angular/platform-browser';
import { HttpClientModule, HTTP_INTERCEPTORS } from '@angular/common/http';
import { FormsModule } from '@angular/forms';
import { RouterModule, Routes } from '@angular/router';

import { AppComponent } from './app.component';
import { LoginComponent } from './login.component';
import { RegisterComponent } from './register.component';
import { ProductListComponent } from './product-list.component';
import { SellerDashboardComponent } from './seller-dashboard.component';
import { MediaManagerComponent } from './media-manager.component';
import { CartComponent } from './cart.component';
import { ClientDashboardComponent } from './client-dashboard.component';
import { TokenInterceptor } from './services/token.interceptor';
import { authGuard, clientGuard, sellerGuard } from './services/auth.guard';

const routes: Routes = [
  { path: '', component: ProductListComponent },
  { path: 'login', component: LoginComponent },
  { path: 'register', component: RegisterComponent },
  { path: 'seller', component: SellerDashboardComponent, canActivate: [authGuard, sellerGuard] },
  { path: 'profile/mes-commandes', component: ClientDashboardComponent, canActivate: [authGuard , clientGuard] }
];

@NgModule({
  declarations: [
    AppComponent,
    LoginComponent,
    RegisterComponent,
    ProductListComponent,
    SellerDashboardComponent,
    MediaManagerComponent,
    CartComponent,
    ClientDashboardComponent
  ],
  imports: [BrowserModule, HttpClientModule, FormsModule, RouterModule.forRoot(routes)],
  providers: [
    { provide: HTTP_INTERCEPTORS, useClass: TokenInterceptor, multi: true }
  ],
  bootstrap: [AppComponent]
})
export class AppModule { }