import { ApplicationConfig, isDevMode, provideZoneChangeDetection } from '@angular/core';
import { provideRouter } from '@angular/router';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { provideAnimationsAsync } from '@angular/platform-browser/animations/async';
import { provideStore } from '@ngrx/store';
import { provideEffects } from '@ngrx/effects';
import { provideStoreDevtools } from '@ngrx/store-devtools';
import { routes } from './app.routes';
import { authInterceptor } from './core/interceptors/auth.interceptor';
import { refreshInterceptor } from './core/interceptors/refresh.interceptor';
import { errorInterceptor } from './core/interceptors/error.interceptor';
import { provideAppI18n } from './core/i18n/translate.providers';
import { NotificationStreamService } from './core/services/notification-stream.service';
import { OpsNotificationStreamService } from './core/services/ops-notification-stream.service';
import { authReducer } from './store/auth/auth.reducer';
import { AuthEffects } from './store/auth/auth.effects';
import { accountsReducer } from './store/accounts/accounts.reducer';
import { AccountsEffects } from './store/accounts/accounts.effects';
import { transfersReducer } from './store/transfers/transfers.reducer';
import { TransfersEffects } from './store/transfers/transfers.effects';

export const appConfig: ApplicationConfig = {
  providers: [
    provideZoneChangeDetection({ eventCoalescing: true }),
    provideRouter(routes),
    provideHttpClient(withInterceptors([authInterceptor, refreshInterceptor, errorInterceptor])),
    provideAnimationsAsync(),
    ...provideAppI18n(),
    // Explicit root providers so admin/customer notification bells always resolve DI
    // (guards against tree-shaking / HMR edge cases with providedIn: 'root' alone).
    NotificationStreamService,
    OpsNotificationStreamService,
    provideStore({
      auth: authReducer,
      accounts: accountsReducer,
      transfers: transfersReducer,
    }),
    provideEffects([AuthEffects, AccountsEffects, TransfersEffects]),
    provideStoreDevtools({ maxAge: 50, logOnly: !isDevMode() }),
  ],
};
