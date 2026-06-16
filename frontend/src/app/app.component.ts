import { Component } from '@angular/core';
import { PotentialSuppliersComponent } from './features/potential-suppliers/potential-suppliers.component';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [PotentialSuppliersComponent],
  template: `
    <header class="app-header">
      <div class="container">
        <h1>Inditex — Potential Suppliers</h1>
        <p class="subtitle">Dashboard for ordering simulations</p>
      </div>
    </header>
    <main class="container">
      <app-potential-suppliers />
    </main>
  `,
  styles: [`
    .app-header {
      background: var(--surface);
      border-bottom: 1px solid var(--border);
      padding: 24px 0;
      margin-bottom: 24px;
    }
    .app-header h1 { margin: 0; font-size: 22px; font-weight: 600; }
    .subtitle { color: var(--muted); margin: 4px 0 0; }
    .container {
      max-width: 1200px;
      margin: 0 auto;
      padding: 0 24px;
    }
  `],
})
export class AppComponent {}
