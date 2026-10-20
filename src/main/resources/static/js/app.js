// ===================================================================
// SMART EXPENSE TRACKER • MODERN SAAS JAVASCRIPT CONTROLLER
// Clean Stripe & Mercury Architecture • Multi-User Isolated Sessions
// ===================================================================

let currentUser = null;
let donutChart = null;
let allTransactions = [];
let currentFilter = 'ALL';

document.addEventListener('DOMContentLoaded', () => {
  restoreFromCache();
  checkAuth();
  loadBotInfo();
  setupModalDismissListeners();

  // Poll for live updates every 15 seconds if logged in
  setInterval(() => {
    if (currentUser) fetchDashboard();
  }, 15000);
});

// Restore cached UI state immediately on page load / refresh (0ms render)
function restoreFromCache() {
  try {
    const cachedUser = localStorage.getItem('set_user');
    if (cachedUser) {
      currentUser = JSON.parse(cachedUser);
      onUserAuthenticated(currentUser);
      hideAuthModal();
    }
    const cachedSummary = localStorage.getItem('set_summary');
    const cachedExpenses = localStorage.getItem('set_expenses');
    const cachedAlerts = localStorage.getItem('set_alerts');
    if (cachedSummary) {
      const summary = JSON.parse(cachedSummary);
      const expenses = cachedExpenses ? JSON.parse(cachedExpenses) : [];
      const alerts = cachedAlerts ? JSON.parse(cachedAlerts) : [];
      updateDashboardUI(summary, expenses, alerts);
    }
  } catch (e) {
    console.warn('Cache restore skipped:', e);
  }
}

// Setup click-outside and ESC key dismiss for modals
function setupModalDismissListeners() {
  document.addEventListener('keydown', (e) => {
    if (e.key === 'Escape') {
      closeBudgetModal();
    }
  });

  const budgetModal = document.getElementById('budget-modal');
  if (budgetModal) {
    budgetModal.addEventListener('click', (e) => {
      if (e.target === budgetModal) {
        closeBudgetModal();
      }
    });
  }
}

async function loadBotInfo() {
  try {
    const res = await fetch('/api/bot-info');
    if (res.ok) {
      const info = await res.json();
      const btn = document.getElementById('btn-open-telegram');
      if (btn && info.username) {
        btn.href = info.url;
        btn.innerHTML = `<span>Open @${info.username}</span> <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M18 13v6a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2h6"></path><polyline points="15 3 21 3 21 9"></polyline><line x1="10" y1="14" x2="21" y2="3"></line></svg>`;
      }
    }
  } catch (ignored) {}
}

// Toast notification helper (Stripe style)
function showToast(message, type = 'success') {
  const container = document.getElementById('toast-container');
  if (!container) return;
  const toast = document.createElement('div');
  toast.className = `toast ${type}`;
  const icon = type === 'success' ? '✓' : type === 'warning' ? '!' : type === 'error' ? '×' : '•';
  toast.innerHTML = `<span style="font-family: var(--font-mono); font-weight: 700; font-size: 1rem;">${icon}</span> <span>${message}</span>`;
  container.appendChild(toast);
  setTimeout(() => {
    toast.style.opacity = '0';
    toast.style.transform = 'translateX(100%)';
    setTimeout(() => toast.remove(), 250);
  }, 3500);
}

// ==========================================
// AUTHENTICATION LOGIC (Parallelized)
// ==========================================

async function checkAuth() {
  try {
    // Fire auth check and dashboard fetch concurrently so there is no waterfall delay
    const [authRes, summaryRes, expensesRes, alertsRes] = await Promise.all([
      fetch('/api/auth/me'),
      fetch('/api/summary'),
      fetch('/api/expenses'),
      fetch('/api/alerts')
    ]);

    if (!authRes.ok || authRes.status === 401) {
      currentUser = null;
      localStorage.removeItem('set_user');
      localStorage.removeItem('set_summary');
      localStorage.removeItem('set_expenses');
      localStorage.removeItem('set_alerts');
      showAuthModal();
      return;
    }

    currentUser = await authRes.json();
    localStorage.setItem('set_user', JSON.stringify(currentUser));
    onUserAuthenticated(currentUser);
    hideAuthModal();

    if (summaryRes.ok && expensesRes.ok && alertsRes.ok) {
      const summary = await summaryRes.json();
      const expensesRaw = await expensesRes.json();
      const alertsRaw = await alertsRes.json();

      localStorage.setItem('set_summary', JSON.stringify(summary));
      localStorage.setItem('set_expenses', JSON.stringify(expensesRaw));
      localStorage.setItem('set_alerts', JSON.stringify(alertsRaw));

      updateDashboardUI(summary, expensesRaw, alertsRaw);
    }
  } catch (err) {
    if (!currentUser) showAuthModal();
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
      localStorage.setItem('set_user', JSON.stringify(currentUser));
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
    btn.innerHTML = `<span>Sign In to Dashboard</span> <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><line x1="5" y1="12" x2="19" y2="12"></line><polyline points="12 5 19 12 12 19"></polyline></svg>`;
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
      localStorage.setItem('set_user', JSON.stringify(currentUser));
      hideAuthModal();
      onUserAuthenticated(currentUser);
      showToast(`Account created. Welcome, ${currentUser.name}!`, 'success');
      fetchDashboard();
    } else {
      errBox.textContent = data.error || 'Registration failed';
      errBox.style.display = 'block';
    }
  } catch (err) {
    errBox.textContent = 'Failed to connect to server';
    errBox.style.display = 'block';
  } finally {
    btn.innerHTML = `<span>Create Account</span> <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><line x1="5" y1="12" x2="19" y2="12"></line><polyline points="12 5 19 12 12 19"></polyline></svg>`;
    btn.disabled = false;
  }
}

async function handleLogout() {
  if (!confirm('Are you sure you want to sign out?')) return;
  try {
    await fetch('/api/auth/logout', { method: 'POST' });
  } catch (ignored) {}
  currentUser = null;
  localStorage.removeItem('set_user');
  localStorage.removeItem('set_summary');
  localStorage.removeItem('set_expenses');
  localStorage.removeItem('set_alerts');
  showToast('Signed out', 'info');
  showAuthModal();
}

function onUserAuthenticated(user) {
  const name = user.name || user.username || 'User';
  document.getElementById('header-user-name').textContent = name;
  document.getElementById('header-user-avatar').textContent = name.charAt(0).toUpperCase();

  const linkCode = user.telegramLinkCode || '------';
  document.getElementById('header-link-code').textContent = linkCode;
  document.getElementById('banner-link-command').textContent = `/link ${linkCode}`;
  
  const cur = user.currency || '₹';
  document.getElementById('sim-currency-symbol').textContent = cur;
  
  const modalCur = document.getElementById('modal-budget-currency');
  if (modalCur) modalCur.textContent = cur;
  const modalCurSym = document.getElementById('modal-currency-sym');
  if (modalCurSym) modalCurSym.textContent = cur;
}

// 1-Click Copy Telegram Link Code
async function copyTelegramLinkCode() {
  const code = (currentUser && currentUser.telegramLinkCode) 
    ? currentUser.telegramLinkCode 
    : document.getElementById('header-link-code').textContent.trim();
  
  const command = `/link ${code}`;
  try {
    await navigator.clipboard.writeText(command);
    showToast(`Copied "${command}" to clipboard! Send to your Telegram bot.`, 'success');
    
    const hintPill = document.getElementById('copy-hint-pill');
    if (hintPill) {
      const original = hintPill.textContent;
      hintPill.textContent = 'Copied!';
      hintPill.style.backgroundColor = '#065f46';
      setTimeout(() => {
        hintPill.textContent = original;
        hintPill.style.backgroundColor = '';
      }, 2000);
    }
  } catch (err) {
    const textarea = document.createElement('textarea');
    textarea.value = command;
    document.body.appendChild(textarea);
    textarea.select();
    document.execCommand('copy');
    textarea.remove();
    showToast(`Copied "${command}"`, 'success');
  }
}

// ==========================================
// PRESET & QUICK-ACTION HELPERS
// ==========================================

function fillSimulator(item, amount) {
  const itemInput = document.getElementById('sim-item');
  const amountInput = document.getElementById('sim-amount');
  if (itemInput && amountInput) {
    itemInput.value = item;
    amountInput.value = amount;
    handleAffordCheck(new Event('submit'));
  }
}

function fillQuickAdd(text) {
  const input = document.getElementById('nlp-text');
  if (input) {
    input.value = text;
    input.focus();
  }
}

// In-Page Category Budget Modal
function openBudgetModal(preselectedCategory = null) {
  const modal = document.getElementById('budget-modal');
  if (!modal) return;
  
  const cur = currentUser ? currentUser.currency : '₹';
  const curSpan = document.getElementById('modal-budget-currency');
  if (curSpan) curSpan.textContent = cur;
  const symSpan = document.getElementById('modal-currency-sym');
  if (symSpan) symSpan.textContent = cur;
  
  if (preselectedCategory) {
    const catSelect = document.getElementById('modal-budget-category');
    if (catSelect) catSelect.value = preselectedCategory;
  }
  
  modal.classList.add('active');
  const amountInput = document.getElementById('modal-budget-amount');
  if (amountInput) amountInput.focus();
}

function closeBudgetModal() {
  const modal = document.getElementById('budget-modal');
  if (modal) modal.classList.remove('active');
}

function setPresetLimit(amount) {
  const amountInput = document.getElementById('modal-budget-amount');
  if (amountInput) {
    amountInput.value = amount;
  }
}

async function handleSaveBudget(e) {
  e.preventDefault();
  const cat = document.getElementById('modal-budget-category').value;
  const limitVal = parseFloat(document.getElementById('modal-budget-amount').value);
  const btn = document.getElementById('modal-save-btn');
  const cur = currentUser ? currentUser.currency : '₹';

  if (isNaN(limitVal) || limitVal < 0) {
    showToast('Enter a valid positive number for the budget limit', 'warning');
    return;
  }

  btn.textContent = 'Saving...';
  btn.disabled = true;

  try {
    const res = await fetch('/api/budgets', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ categoryName: cat, limit: limitVal })
    });

    if (res.ok) {
      showToast(`Updated monthly limit for ${cat} to ${cur}${limitVal.toFixed(2)}`, 'success');
      closeBudgetModal();
      document.getElementById('modal-budget-amount').value = '';
      fetchDashboard();
    } else {
      const err = await res.json();
      showToast(err.error || 'Failed to update category budget', 'error');
    }
  } catch (err) {
    showToast('Failed to connect to server', 'error');
  } finally {
    btn.innerHTML = `<span>Save Category Budget</span>`;
    btn.disabled = false;
  }
}

// Transaction Ledger Filter
function filterTransactions(type, btnElement) {
  currentFilter = type;
  
  const pills = document.querySelectorAll('.filter-btn');
  pills.forEach(p => p.classList.remove('active'));
  if (btnElement) btnElement.classList.add('active');

  applyTransactionFilter();
}

function applyTransactionFilter() {
  const cur = currentUser ? currentUser.currency : '₹';
  let filtered = allTransactions;

  if (currentFilter === 'EXPENSE') {
    filtered = allTransactions.filter(t => t.type !== 'INCOME');
  } else if (currentFilter === 'INCOME') {
    filtered = allTransactions.filter(t => t.type === 'INCOME');
  } else if (currentFilter === 'TELEGRAM') {
    filtered = allTransactions.filter(t => t.source === 'TELEGRAM');
  }

  renderTransactionsTable(filtered, cur);
}

// ==========================================
// DASHBOARD LOGIC
// ==========================================

function updateDashboardUI(summary, expensesRaw, alertsRaw) {
  allTransactions = Array.isArray(expensesRaw) ? expensesRaw : [];
  const alerts = Array.isArray(alertsRaw) ? alertsRaw : [];
  const cur = (summary && summary.currency) || (currentUser && currentUser.currency) || '₹';

  if (summary && summary.safeToSpendToday !== undefined) {
    renderKPIs(summary, alerts, cur);
    renderCategoryBudgets(summary.categoryStatuses, cur);
    renderDonutChart(summary.categoryStatuses, cur);
  }
  renderAlerts(alerts);
  applyTransactionFilter();
}

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

    const summary = summaryRes.ok ? await summaryRes.json() : {};
    const expensesRaw = expensesRes.ok ? await expensesRes.json() : [];
    const alertsRaw = alertsRes.ok ? await alertsRes.json() : [];

    localStorage.setItem('set_summary', JSON.stringify(summary));
    localStorage.setItem('set_expenses', JSON.stringify(expensesRaw));
    localStorage.setItem('set_alerts', JSON.stringify(alertsRaw));

    updateDashboardUI(summary, expensesRaw, alertsRaw);
  } catch (err) {
    console.error('Error fetching dashboard:', err);
  }
}

function renderKPIs(summary, alerts, cur) {
  // Metric 1: Safe daily spend
  const safeToday = summary.safeToSpendToday || 0;
  document.getElementById('kpi-safe-today').textContent = `${cur}${safeToday.toFixed(2)}`;
  
  const baselineDaily = (summary.totalBudget > 0) ? (summary.totalBudget / 30) : 500;
  const allowanceRatio = Math.min(100, Math.max(5, (safeToday / (baselineDaily || 1)) * 100));
  const allowanceFill = document.getElementById('kpi-allowance-fill');
  if (allowanceFill) {
    allowanceFill.style.width = `${allowanceRatio}%`;
    if (safeToday <= 0) {
      allowanceFill.className = 'saas-progress-fill';
      allowanceFill.style.backgroundColor = '#ef4444';
    } else if (allowanceRatio < 40) {
      allowanceFill.className = 'saas-progress-fill';
      allowanceFill.style.backgroundColor = '#f59e0b';
    } else {
      allowanceFill.className = 'saas-progress-fill bg-emerald';
    }
  }

  // Metric 2: Spent this month
  document.getElementById('kpi-spent-month').textContent = `${cur}${summary.totalSpent.toFixed(2)}`;
  if (summary.totalIncome > 0) {
    document.getElementById('kpi-budget-total').textContent = `Budget: ${cur}${summary.totalBudget.toFixed(2)} | Income: +${cur}${summary.totalIncome.toFixed(2)}`;
  } else {
    document.getElementById('kpi-budget-total').textContent = `Budget: ${cur}${summary.totalBudget.toFixed(2)} (${summary.percentBudgetUsed}% used)`;
  }
  const spentFill = document.getElementById('kpi-spent-fill');
  if (spentFill) {
    spentFill.style.width = `${Math.min(100, Math.max(0, summary.percentBudgetUsed))}%`;
  }

  // Metric 3: Liquid remaining
  document.getElementById('kpi-remaining').textContent = `${cur}${summary.remainingBudget.toFixed(2)}`;
  document.getElementById('kpi-days-left').textContent = `${summary.daysRemainingInMonth} days left in cycle`;

  // Metric 4: Guardrail status
  const alertCount = alerts ? alerts.length : 0;
  document.getElementById('kpi-alerts-count').textContent = alertCount;
  
  const velocityChip = document.getElementById('velocity-status-chip');
  const velocityLabel = document.getElementById('velocity-status-label');
  if (velocityChip && velocityLabel) {
    const hasCritical = alerts && alerts.some(a => a.severity === 'CRITICAL');
    const hasWarning = alerts && alerts.some(a => a.severity === 'WARNING');

    if (hasCritical || summary.percentBudgetUsed > 90) {
      velocityChip.className = 'velocity-pill danger';
      velocityLabel.textContent = 'Critical Burn Pace';
    } else if (hasWarning || summary.percentBudgetUsed > 75) {
      velocityChip.className = 'velocity-pill warning';
      velocityLabel.textContent = 'High Velocity';
    } else {
      velocityChip.className = 'velocity-pill normal';
      velocityLabel.textContent = 'Normal Velocity';
    }
  }
}

function renderCategoryBudgets(categories, cur) {
  const container = document.getElementById('category-budget-container');
  if (!categories || categories.length === 0) {
    container.innerHTML = '<div style="color: var(--text-muted); font-size: 0.85rem; padding: 12px 0;">No category limits configured. Select "+ Adjust Limit" to set thresholds.</div>';
    return;
  }

  container.innerHTML = categories.map(cat => {
    const percent = Math.min(100, Math.max(0, cat.percentUsed));
    const isExceeded = cat.status === 'EXCEEDED';
    const isWarning = cat.status === 'WARNING';
    const color = isExceeded ? '#ef4444' : (isWarning ? '#f59e0b' : (cat.colorHex || '#4f46e5'));
    
    return `
      <div class="budget-item" onclick="openBudgetModal('${cat.categoryName}')" title="Adjust limit for ${cat.categoryName}">
        <div class="budget-item-top">
          <div class="budget-cat-name">
            <span>${cat.categoryName}</span>
          </div>
          <div class="budget-cat-values">
            <strong>${cur}${cat.spent.toFixed(2)}</strong> / <span>${cur}${cat.limit.toFixed(2)}</span>
            <span style="color: ${color}; font-weight: 700; margin-left: 6px;">
              (${cat.percentUsed}%)
            </span>
          </div>
        </div>
        <div class="saas-progress-track">
          <div class="saas-progress-fill" style="width: ${percent}%; background-color: ${color};"></div>
        </div>
      </div>
    `;
  }).join('');
}

// Modern Stripe / Mercury Palette for Donut Chart
function renderDonutChart(categories, cur) {
  const activeCategories = (categories || []).filter(c => c.spent > 0);
  const canvas = document.getElementById('categoryDonutChart');
  if (!canvas) return;
  const ctx = canvas.getContext('2d');

  if (activeCategories.length === 0) {
    if (donutChart) {
      donutChart.destroy();
      donutChart = null;
    }
    return;
  }

  const stripePalette = [
    '#4f46e5', // Indigo
    '#0284c7', // Cyan
    '#10b981', // Emerald
    '#f59e0b', // Amber
    '#ec4899', // Pink
    '#8b5cf6', // Violet
    '#64748b'  // Slate
  ];

  const labels = activeCategories.map(c => c.categoryName);
  const data = activeCategories.map(c => c.spent);
  const colors = activeCategories.map((c, i) => stripePalette[i % stripePalette.length]);

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
          borderColor: '#ffffff',
          borderWidth: 2,
          hoverOffset: 4
        }]
      },
      options: {
        responsive: true,
        maintainAspectRatio: false,
        plugins: {
          legend: {
            position: 'bottom',
            labels: {
              color: '#475569',
              font: { family: 'Inter', size: 12, weight: 500 },
              padding: 12,
              boxWidth: 10
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
    container.innerHTML = `
      <div style="color: var(--text-muted); font-size: 0.85rem; padding: 24px 0; text-align: center;">
        No active velocity warnings or threshold breaches detected. All systems healthy.
      </div>
    `;
    return;
  }

  container.innerHTML = alerts.map(a => `
    <div class="alert-item severity-${a.severity}">
      <div class="alert-item-title">
        <span>${a.title}</span>
        <span class="alert-item-time">${formatDate(a.createdAt)}</span>
      </div>
      <div class="alert-item-msg">${a.message}</div>
      ${a.remedySuggestion ? `<div class="alert-item-remedy">Recommendation: ${a.remedySuggestion}</div>` : ''}
    </div>
  `).join('');
}

function renderTransactionsTable(expenses, cur) {
  const tbody = document.getElementById('tx-table-body');
  if (!tbody) return;

  if (!Array.isArray(expenses) || expenses.length === 0) {
    tbody.innerHTML = '<tr><td colspan="5" style="text-align: center; color: var(--text-muted); padding: 28px;">No transactions match this filter.</td></tr>';
    return;
  }

  tbody.innerHTML = expenses.map(tx => {
    const catName = tx.category ? tx.category.name : 'General';
    const isIncome = tx.type === 'INCOME';
    return `
      <tr>
        <td>
          <div class="tx-desc-bold">
            ${tx.description || 'Transaction'}
            ${isIncome ? '<span style="background-color: #ecfdf5; color: #059669; font-size: 0.72rem; padding: 2px 7px; border-radius: 4px; margin-left: 6px; font-weight: 700;">+INCOME</span>' : ''}
          </div>
          <div class="tx-date-sub">${tx.date || ''}</div>
        </td>
        <td>
          <span style="font-size: 0.84rem; font-weight: 500;">${catName}</span>
        </td>
        <td>
          <span class="tx-channel-badge source-${tx.source}">${tx.source === 'TELEGRAM' ? 'Telegram' : 'Web'}</span>
        </td>
        <td>
          <span class="tx-amount-mono ${isIncome ? 'income' : ''}">${isIncome ? '+' : '-'}${cur}${tx.amount.toFixed(2)}</span>
        </td>
        <td style="text-align: right;">
          <button class="btn-trash" onclick="deleteExpense(${tx.id})" title="Delete entry">
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polyline points="3 6 5 6 21 6"></polyline><path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2"></path></svg>
          </button>
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
      showToast(`Recorded ${cur}${data.expense.amount.toFixed(2)} for ${data.expense.description}`, 'success');
      
      if (data.alerts && data.alerts.length > 0) {
        showToast(data.alerts[0].title, 'warning');
      }

      fetchDashboard();
    } else {
      showToast('Could not recognize amount. Example: Canteen 50 or Salary 5000', 'error');
    }
  } catch (err) {
    showToast('Failed to connect to server', 'error');
  } finally {
    btn.innerHTML = `<span>Record</span> <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><line x1="12" y1="5" x2="12" y2="19"></line><line x1="5" y1="12" x2="19" y2="12"></line></svg>`;
    btn.disabled = false;
  }
}

async function handleAffordCheck(e) {
  if (e && e.preventDefault) e.preventDefault();
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

    badge.className = `verdict-tag verdict-${data.verdict}`;
    badge.textContent = data.verdict === 'SAFE' ? 'SAFE TO BUY' : (data.verdict === 'CAUTION' ? 'TIGHTENS ALLOWANCE' : 'OVER BUDGET');
    headline.textContent = data.headline.replace(/[\u{1F300}-\u{1F9FF}]/gu, '').replace(/[\u2600-\u26FF]/g, '').trim();
    details.textContent = data.details;
    curDaily.textContent = `${cur}${data.currentSafeDaily.toFixed(2)}/day`;
    newDaily.textContent = `${cur}${data.newSafeDaily.toFixed(2)}/day`;

    resultBox.classList.add('active');
  } catch (err) {
    showToast('Failed to calculate affordability check', 'error');
  } finally {
    btn.innerHTML = `<span>Simulate</span> <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polyline points="9 18 15 12 9 6"></polyline></svg>`;
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

function formatDate(isoString) {
  if (!isoString) return '';
  const d = new Date(isoString);
  return d.toLocaleDateString('en-US', { month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit' });
}
