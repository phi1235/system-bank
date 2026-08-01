import { Component, OnInit, inject } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { Store } from '@ngrx/store';
import { AuthActions } from './store/auth/auth.actions';
import { ExportQueueWidgetComponent } from './shared/components/export-queue-widget/export-queue-widget.component';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [RouterOutlet, ExportQueueWidgetComponent],
  templateUrl: './app.component.html',
  styleUrl: './app.component.scss',
})
export class AppComponent implements OnInit {
  private readonly store = inject(Store);

  ngOnInit(): void {
    this.store.dispatch(AuthActions.bootstrap());
  }
}
