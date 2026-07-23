import { ComponentFixture, TestBed } from '@angular/core/testing';
import { LoginComponent } from './login.component';
import { AuthService } from './services/auth.service';
import { Router } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { of, throwError } from 'rxjs';

describe('LoginComponent', () => {
  let component: LoginComponent;
  let fixture: ComponentFixture<LoginComponent>;
  let authServiceSpy: jasmine.SpyObj<AuthService>;
  let routerSpy: jasmine.SpyObj<Router>;

  beforeEach(async () => {
    authServiceSpy = jasmine.createSpyObj('AuthService', ['login', 'setToken']);
    routerSpy = jasmine.createSpyObj('Router', ['navigate']);

    await TestBed.configureTestingModule({
      imports: [FormsModule],
      declarations: [LoginComponent],
      providers: [
        { provide: AuthService, useValue: authServiceSpy },
        { provide: Router, useValue: routerSpy }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(LoginComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should login successfully and navigate to root', () => {
    const mockEvent = new Event('submit');
    spyOn(mockEvent, 'preventDefault');
    authServiceSpy.login.and.returnValue(of({ token: 'fake-jwt-token' }));

    component.email = 'test@test.com';
    component.password = 'password123';
    component.login(mockEvent);

    expect(mockEvent.preventDefault).toHaveBeenCalled();
    expect(authServiceSpy.login).toHaveBeenCalledWith({ email: 'test@test.com', password: 'password123' });
    expect(authServiceSpy.setToken).toHaveBeenCalledWith('fake-jwt-token');
    expect(routerSpy.navigate).toHaveBeenCalledWith(['/']);
  });

  it('should handle login error', () => {
    const mockEvent = new Event('submit');
    spyOn(mockEvent, 'preventDefault');
    authServiceSpy.login.and.returnValue(throwError(() => ({ error: { error: 'Invalid credentials' } })));

    component.login(mockEvent);

    expect(component.error).toBe('Invalid credentials');
  });
});