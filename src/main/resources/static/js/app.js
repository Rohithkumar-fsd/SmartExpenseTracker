// ===================================================================
// SMART EXPENSE TRACKER - MULTI-USER CLIENT JAVASCRIPT
// ===================================================================

let currentUser = null;
let donutChart = null;

document.addEventListener('DOMContentLoaded', () => {
  checkAuth();
  loadBotInfo();
  // Poll for live updates every 15 seconds if logged in
  setInterval(() => {
    if (currentUser) fetchDashboard();
  }, 15000);
});

async function loadBotInfo() {
  try {
    const res = await fetch('/api/bot-info');
    if (res.ok) {
      const info = await res.json();
      const btn = document.getElementById('btn-open-telegram');
      if (btn && info.username) {
        btn.href = info.url;
        btn.textContent = `Open @${info.username} ↗`;
      }
    }
  } catch (ignored) {}
}

// Toast notification helper
function showToast(message, type = 'success') {
  const container = document.getElementById('toast-container');
  const toast = document.createElement('div');
  toast.className = `toast ${type}`;
  toast.innerHTML = `<span>${type === 'success' ? '✅' : type === 'warning' ? '⚠️' : 'ℹ️'}</span> <span>${message}</span>`;
  container.appendChild(toast);
  setTimeout(() => {
    toast.style.opacity = '0';
    toast.style.transform = 'translateX(100%)';
    setTimeout(() => toast.remove(), 300);
  }, 4000);
}

// ==========================================
// AUTHENTICATION LOGIC
// ==========================================

async function checkAuth() {
  try {
    const res = await fetch('/api/auth/me');
    if (res.ok) {
      currentUser = await res.json();
      onUserAuthenticated(currentUser);
      fetchDashboard();
    } else {
      showAuthModal();
    }
  } catch (err) {
    showAuthModal();
  }
}

function showAuthModal() {
  document.getElementById('auth-modal').classList.add('active');
}

function hideAuthModal() {
  document.getElementById('auth-modal').classList.remove('active');
}

function switchAuthTab(tab) {
  document.getElementById('auth-error').style.display = 'none';
  if (tab === 'login') {
    document.getElementById('tab-login').classList.add('active');
    document.getElementById('tab-signup').classList.remove('active');
    document.getElementById('login-form').style.display = 'block';
    document.getElementById('signup-form').style.display = 'none';
  } else {
    document.getElementById('tab-signup').classList.add('active');
    document.getElementById('tab-login').classList.remove('active');
    document.getElementById('login-form').style.display = 'none';
    document.getElementById('signup-form').style.display = 'block';
  }
}

async function handleLoginSubmit(e) {
  e.preventDefault();
  const usernameOrEmail = document.getElementById('login-username').value.trim();
  const password = document.getElementById('login-password').value;
  const btn = document.getElementById('login-btn');
  const errBox = document.getElementById('auth-error');

  btn.textContent = 'Signing in...';
  btn.disabled = true;
  errBox.style.display = 'none';

  try {
    const res = await fetch('/api/auth/login', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ usernameOrEmail, password })
    });

    const data = await res.json();
    if (res.ok) {
      currentUser = data;
      hideAuthModal();
      onUserAuthenticated(currentUser);
      showToast(`Welcome back, ${currentUser.name}!`, 'success');
      fetchDashboard();
    } else {
      errBox.textContent = data.error || 'Invalid credentials';
      errBox.style.display = 'block';
    }
  } catch (err) {
    errBox.textContent = 'Failed to connect to server';
    errBox.style.display = 'block';
  } finally {
    btn.textContent = 'Sign In';
    btn.disabled = false;
  }
}

async function handleSignupSubmit(e) {
  e.preventDefault();
  const name = document.getElementById('signup-name').value.trim();
  const username = document.getElementById('signup-username').value.trim();
  const email = document.getElementById('signup-email').value.trim();
  const password = document.getElementById('signup-password').value;
  const currency = document.getElementById('signup-currency').value;
  const btn = document.getElementById('signup-btn');
  const errBox = document.getElementById('auth-error');

  btn.textContent = 'Creating account...';
  btn.disabled = true;
  errBox.style.display = 'none';

  try {
    const res = await fetch('/api/auth/signup', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ name, username, email, password, currency })
    });

    const data = await res.json();
    if (res.ok) {
      currentUser = data;
      hideAuthModal();
      onUserAuthenticated(currentUser);
      showToast(`Account created! Welcome, ${currentUser.name}!`, 'success');
      fetchDashboard();
    } else {
      errBox.textContent = data.error || 'Registration failed';
      errBox.style.display = 'block';
    }
  } catch (err) {
    errBox.textContent = 'Failed to connect to server';
    errBox.style.display = 'block';
  } finally {
    btn.textContent = 'Create Account';
    btn.disabled = false;
  }
}

async function handleLogout() {
  if (!confirm('Are you sure you want to log out?')) return;
  try {
    await fetch('/api/auth/logout', { method: 'POST' });
  } catch (ignored) {}
  currentUser = null;
  showToast('Logged out', 'info');
  showAuthModal();
}

function onUserAuthenticated(user) {
  document.getElementById('header-user-name').textContent = user.name || user.username;
  document.getElementById('header-user-avatar').textContent = (user.name || user.username).charAt(0).toUpperCase();

  const linkCode = user.telegramLinkCode || '------';
  document.getElementById('header-link-code').textContent = linkCode;
  document.getElementById('banner-link-command').textContent = `/link ${linkCode}`;
  document.getElementById('sim-currency-symbol').textContent = user.currency || '₹';
}

// ==========================================
// DASHBOARD LOGIC
// ==========================================

async function fetchDashboard() {
  if (!currentUser) return;
  try {
    const [summaryRes, expensesRes, alertsRes] = await Promise.all([
      fetch('/api/summary'),
      fetch('/api/expenses'),
      fetch('/api/alerts')
    ]);

    if (summaryRes.status === 401) {
      showAuthModal();
      return;
    }

    const summary = await summaryRes.json();
    const expenses = await expensesRes.json();
    const alerts = await alertsRes.json();

    const cur = summary.currency || currentUser.currency || '₹';

    renderKPIs(summary, alerts, cur);
    renderCategoryBudgets(summary.categoryStatuses, cur);
    renderDonutChart(summary.categoryStatuses, cur);
    renderAlerts(alerts);
    renderTransactions(expenses, cur);
  } catch (err) {
    console.error('Error fetching dashboard:', err);
  }
}

function renderKPIs(summary, alerts, cur) {
  document.getElementById('kpi-safe-today').textContent = `${cur}${summary.safeToSpendToday.toFixed(2)}`;
  document.getElementById('kpi-spent-month').textContent = `${cur}${summary.totalSpent.toFixed(2)}`;
  if (summary.totalIncome > 0) {
    document.getElementById('kpi-budget-total').textContent = `Budget: ${cur}${summary.totalBudget.toFixed(2)} | Income: +${cur}${summary.totalIncome.toFixed(2)}`;
  } else {
    document.getElementById('kpi-budget-total').textContent = `Budget: ${cur}${summary.totalBudget.toFixed(2)} (${summary.percentBudgetUsed}% used)`;
  }
  document.getElementById('kpi-remaining').textContent = `${cur}${summary.remainingBudget.toFixed(2)}`;
  document.getElementById('kpi-days-left').textContent = `${summary.daysRemainingInMonth} days left in month`;
  document.getElementById('kpi-alerts-count').textContent = alerts.length;
}

function renderCategoryBudgets(categories, cur) {
  const container = document.getElementById('category-budget-container');
  if (!categories || categories.length === 0) {
    container.innerHTML = '<div style="color: var(--text-muted); font-size: 0.9rem;">No category budgets set yet.</div>';
    return;
  }

  container.innerHTML = categories.map(cat => {
    const percent = Math.min(100, cat.percentUsed);
    const color = cat.status === 'EXCEEDED' ? '#f43f5e' : (cat.status === 'WARNING' ? '#f59e0b' : cat.colorHex);
    return `
      <div class="budget-item">
        <div class="budget-item-top">
          <div class="budget-cat-name">
            <span>${cat.icon}</span>
            <span>${cat.categoryName}</span>
          </div>
          <div class="budget-cat-values">
            <strong>${cur}${cat.spent.toFixed(2)}</strong> / ${cur}${cat.limit.toFixed(2)} 
            <span style="color: ${color}; font-weight: 700; margin-left: 6px;">(${cat.percentUsed}%)</span>
          </div>
        </div>
        <div class="progress-track">
          <div class="progress-fill" style="width: ${percent}%; background: ${color};"></div>
        </div>
      </div>
    `;
  }).join('');
}

function renderDonutChart(categories, cur) {
  const activeCategories = (categories || []).filter(c => c.spent > 0);
  const ctx = document.getElementById('categoryDonutChart').getContext('2d');

  if (activeCategories.length === 0) {
    if (donutChart) donutChart.destroy();
    return;
  }

  const labels = activeCategories.map(c => `${c.icon} ${c.categoryName}`);
  const data = activeCategories.map(c => c.spent);
  const colors = activeCategories.map(c => c.colorHex || '#6366f1');

  if (donutChart) {
    donutChart.data.labels = labels;
    donutChart.data.datasets[0].data = data;
    donutChart.data.datasets[0].backgroundColor = colors;
    donutChart.update();
  } else {
    donutChart = new Chart(ctx, {
      type: 'doughnut',
      data: {
        labels: labels,
        datasets: [{
          data: data,
          backgroundColor: colors,
          borderColor: '#0f172a',
          borderWidth: 3,
          hoverOffset: 6
        }]
      },
      options: {
        responsive: true,
        maintainAspectRatio: false,
        plugins: {
          legend: {
            position: 'bottom',
            labels: {
              color: '#94a3b8',
              font: { family: 'Plus Jakarta Sans', size: 12 },
              padding: 14,
              boxWidth: 12
            }
          },
          tooltip: {
            callbacks: {
              label: function(context) {
                return ` ${cur}${context.parsed.toFixed(2)}`;
              }
            }
          }
        },
        cutout: '72%'
      }
    });
  }
}

function renderAlerts(alerts) {
  const container = document.getElementById('alerts-container');
  if (!alerts || alerts.length === 0) {
    container.innerHTML = '<div style="color: var(--text-muted); font-size: 0.88rem; padding: 20px 0;">🎉 All clear! No spending velocity warnings or budget breaches.</div>';
    return;
  }

  container.innerHTML = alerts.map(a => `
    <div class="alert-item severity-${a.severity}">
      <div class="alert-item-title">
        <span>${a.title}</span>
        <span class="alert-item-time">${formatDate(a.createdAt)}</span>
      </div>
      <div class="alert-item-msg">${a.message}</div>
      ${a.remedySuggestion ? `<div class="alert-item-remedy">💡 ${a.remedySuggestion}</div>` : ''}
    </div>
  `).join('');
}

function renderTransactions(expenses, cur) {
  const tbody = document.getElementById('tx-table-body');
  if (!expenses || expenses.length === 0) {
    tbody.innerHTML = '<tr><td colspan="5" style="text-align: center; color: var(--text-muted); padding: 20px;">No expenses logged yet. Try typing above!</td></tr>';
    return;
  }

  tbody.innerHTML = expenses.map(tx => {
    const icon = tx.category ? tx.category.icon : '💰';
    const catName = tx.category ? tx.category.name : 'Other';
    const isIncome = tx.type === 'INCOME';
    return `
      <tr>
        <td>
          <div style="font-weight: 600;">
            ${tx.description || 'Expense'}
            ${isIncome ? '<span style="background: rgba(16, 185, 129, 0.2); color: #10b981; padding: 2px 7px; border-radius: 4px; font-size: 0.7rem; font-weight: 700; margin-left: 6px;">+INCOME</span>' : ''}
          </div>
          <div style="font-size: 0.76rem; color: var(--text-muted);">${tx.date}</div>
        </td>
        <td>
          <span style="font-size: 0.85rem;">${icon} ${catName}</span>
        </td>
        <td>
          <span class="tx-source-badge tx-source-${tx.source}">${tx.source === 'TELEGRAM' ? '🤖 Bot' : '💻 Web'}</span>
        </td>
        <td>
          <span class="tx-amount" style="${isIncome ? 'color: #10b981; font-weight: 700;' : ''}">${isIncome ? '+' : '-'}${cur}${tx.amount.toFixed(2)}</span>
        </td>
        <td style="text-align: right;">
          <button class="tx-delete-btn" onclick="deleteExpense(${tx.id})" title="Delete">✕</button>
        </td>
      </tr>
    `;
  }).join('');
}

async function handleQuickAdd(e) {
  e.preventDefault();
  const input = document.getElementById('nlp-text');
  const text = input.value.trim();
  if (!text) return;

  const btn = document.getElementById('log-btn');
  btn.textContent = 'Saving...';
  btn.disabled = true;

  try {
    const res = await fetch('/api/expenses', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ rawText: text })
    });

    if (res.ok) {
      const data = await res.json();
      input.value = '';
      const cur = currentUser ? currentUser.currency : '₹';
      showToast(`Logged ${cur}${data.expense.amount.toFixed(2)} for ${data.expense.description}!`, 'success');
      
      if (data.alerts && data.alerts.length > 0) {
        showToast(data.alerts[0].title, 'warning');
      }

      fetchDashboard();
    } else {
      showToast('Could not recognize amount. Example: Canteen 50', 'error');
    }
  } catch (err) {
    showToast('Failed to connect to server', 'error');
  } finally {
    btn.textContent = 'Log';
    btn.disabled = false;
  }
}

async function handleAffordCheck(e) {
  e.preventDefault();
  const amountInput = document.getElementById('sim-amount');
  const itemInput = document.getElementById('sim-item');
  const btn = document.getElementById('sim-btn');

  const amount = parseFloat(amountInput.value);
  const item = itemInput.value.trim();
  if (!amount || !item) return;

  btn.textContent = 'Analyzing...';
  btn.disabled = true;

  try {
    const res = await fetch(`/api/afford-check?amount=${amount}&item=${encodeURIComponent(item)}`);
    const data = await res.json();

    const resultBox = document.getElementById('sim-result');
    const badge = document.getElementById('sim-badge');
    const headline = document.getElementById('sim-headline');
    const details = document.getElementById('sim-details');
    const curDaily = document.getElementById('sim-current-daily');
    const newDaily = document.getElementById('sim-new-daily');
    const cur = currentUser ? currentUser.currency : '₹';

    badge.className = `sim-verdict-badge verdict-${data.verdict}`;
    badge.textContent = data.verdict;
    headline.textContent = data.headline;
    details.textContent = data.details;
    curDaily.textContent = `${cur}${data.currentSafeDaily.toFixed(2)}/day`;
    newDaily.textContent = `${cur}${data.newSafeDaily.toFixed(2)}/day`;

    resultBox.classList.add('active');
  } catch (err) {
    showToast('Failed to calculate affordability check', 'error');
  } finally {
    btn.textContent = 'Simulate';
    btn.disabled = false;
  }
}

async function deleteExpense(id) {
  if (!confirm('Are you sure you want to remove this transaction?')) return;
  try {
    const res = await fetch(`/api/expenses/${id}`, { method: 'DELETE' });
    if (res.ok) {
      showToast('Transaction removed', 'success');
      fetchDashboard();
    }
  } catch (err) {
    showToast('Failed to delete transaction', 'error');
  }
}

async function openBudgetModal() {
  const cat = prompt('Enter category name (e.g. Food & Dining, Transport, Shopping):');
  if (!cat) return;
  const cur = currentUser ? currentUser.currency : '₹';
  const limitStr = prompt(`Enter monthly limit for "${cat}" in ${cur}:`, '2000');
  if (!limitStr) return;
  const limit = parseFloat(limitStr);
  if (isNaN(limit) || limit < 0) {
    alert('Please enter a valid positive number');
    return;
  }

  try {
    const res = await fetch('/api/budgets', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ categoryName: cat, limit: limit })
    });
    if (res.ok) {
      showToast(`Updated budget for ${cat} to ${cur}${limit}`, 'success');
      fetchDashboard();
    }
  } catch (err) {
    showToast('Failed to update budget', 'error');
  }
}

function formatDate(isoString) {
  if (!isoString) return '';
  const d = new Date(isoString);
  return d.toLocaleDateString('en-US', { month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit' });
}
