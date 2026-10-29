/* MyMoney — dependency-free, offline-first finance app for the web, Android and Windows.
 * Money is always stored and calculated as integer minor units (cents).
 */

const STORAGE_KEY = "mymoney.desktop.v1";
const launchParams = new URLSearchParams(location.search);
let demoMode = launchParams.get("demo") === "1";
let installPrompt = null;
const $ = (selector, root = document) => root.querySelector(selector);
const $$ = (selector, root = document) => [...root.querySelectorAll(selector)];
const isoDate = date => `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, "0")}-${String(date.getDate()).padStart(2, "0")}`;
const todayIso = () => isoDate(new Date());
const monthKey = (date = todayIso()) => date.slice(0, 7);
const uid = () => crypto.randomUUID();
const symbols = { EUR: "€", USD: "$", GBP: "£", CHF: "CHF ", SEK: "kr ", NOK: "kr ", DKK: "kr ", PLN: "zł ", CZK: "Kč " };
const categoryIcons = { Groceries: "🛒", "Eating out": "☕", Transport: "🚗", Housing: "🏠", Utilities: "⚡", Shopping: "🛍️", Entertainment: "🎬", Health: "✚", Travel: "✈️", Education: "🎓", Salary: "💼", Gifts: "🎁", Interest: "%", Other: "•••" };
const navItems = [
  ["OVERVIEW"],
  ["dashboard", "⌂", "Dashboard"],
  ["transactions", "↕", "Transactions"],
  ["accounts", "▣", "Accounts"],
  ["PLANNING"],
  ["budgets", "◔", "Budgets"],
  ["goals", "◇", "Savings goals"],
  ["bills", "▤", "Bills & subscriptions"],
  ["debts", "⚖", "Debts & loans"],
  ["INSIGHTS"],
  ["planning", "↗", "Planning"],
  ["reports", "▥", "Reports"],
  ["settings", "⚙", "Settings"],
];
const pageMeta = {
  dashboard: ["OVERVIEW", "Dashboard"],
  transactions: ["MONEY ACTIVITY", "Transactions"],
  accounts: ["YOUR MONEY", "Accounts"],
  budgets: ["SPENDING PLAN", "Budgets"],
  goals: ["BUILD YOUR FUTURE", "Savings goals"],
  bills: ["UPCOMING PAYMENTS", "Bills & subscriptions"],
  debts: ["WHAT YOU OWE", "Debts & loans"],
  planning: ["WHAT-IF CALCULATORS", "Planning"],
  reports: ["INSIGHTS", "Reports"],
  settings: ["PREFERENCES & DATA", "Settings"],
};

function parseCents(raw) {
  let value = String(raw ?? "").trim().replace(/[€$£\s']/g, "");
  if (!value || value.startsWith("-")) return null;
  const dot = value.lastIndexOf(".");
  const comma = value.lastIndexOf(",");
  if (dot >= 0 && comma >= 0) {
    value = dot > comma ? value.replaceAll(",", "") : value.replaceAll(".", "").replace(",", ".");
  } else if (comma >= 0) {
    const decimals = value.length - comma - 1;
    value = value.split(",").length === 2 && decimals <= 2 ? value.replace(",", ".") : value.replaceAll(",", "");
  } else if (dot >= 0) {
    const decimals = value.length - dot - 1;
    if (value.split(".").length > 2) return null;
    if (decimals === 3) value = value.replace(".", "");
    else if (decimals > 2) return null;
  }
  if (value.endsWith(".")) value = value.slice(0, -1);
  if (!/^\d+(\.\d{0,2})?$/.test(value)) return null;
  const [whole, fraction = ""] = value.split(".");
  const cents = Number(whole) * 100 + Number((fraction + "00").slice(0, 2));
  return Number.isSafeInteger(cents) ? cents : null;
}

function money(cents, signed = false) {
  const code = state?.settings?.currency || "EUR";
  const sign = cents < 0 ? "−" : signed && cents > 0 ? "+" : "";
  const absolute = Math.abs(cents);
  const whole = String(Math.floor(absolute / 100)).replace(/\B(?=(\d{3})+(?!\d))/g, ",");
  return `${sign}${symbols[code] || `${code} `}${whole}.${String(absolute % 100).padStart(2, "0")}`;
}
const plural = (count, word, many = `${word}s`) => `${count} ${count === 1 ? word : many}`;
// The interface is English, so dates stay English even on phones set to another language.
const dateLocale = (navigator.languages?.length ? navigator.languages : [navigator.language]).find(language => /^en\b/i.test(language)) || "en-IE";
const amountInput = cents => cents ? `${Math.floor(cents / 100)}.${String(cents % 100).padStart(2, "0")}` : "";
const escapeHtml = value => String(value ?? "").replace(/[&<>"']/g, char => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#039;" })[char]);
const dateLabel = value => new Date(`${value}T12:00:00`).toLocaleDateString(dateLocale, { day: "numeric", month: "short", year: "numeric" });
const monthLabel = value => new Date(`${value}-01T12:00:00`).toLocaleDateString(dateLocale, { month: "long", year: "numeric" });
const themeIcons = {
  moon: `<svg viewBox="0 0 24 24" width="18" height="18" aria-hidden="true"><path d="M20.5 14.3A8.5 8.5 0 0 1 9.7 3.5a8.5 8.5 0 1 0 10.8 10.8Z" fill="none" stroke="currentColor" stroke-width="2" stroke-linejoin="round"/></svg>`,
  sun: `<svg viewBox="0 0 24 24" width="18" height="18" aria-hidden="true"><circle cx="12" cy="12" r="4.2" fill="none" stroke="currentColor" stroke-width="2"/><path d="M12 2.5v2.2M12 19.3v2.2M2.5 12h2.2M19.3 12h2.2M5.3 5.3l1.6 1.6M17.1 17.1l1.6 1.6M5.3 18.7l1.6-1.6M17.1 6.9l1.6-1.6" stroke="currentColor" stroke-width="2" stroke-linecap="round"/></svg>`,
};
const clamp = (value, min, max) => Math.max(min, Math.min(max, value));
const percent = (part, whole) => whole > 0 ? Math.round(part * 1000 / whole) / 10 : 0;

function emptyData() {
  return {
    version: 1,
    settings: { currency: "EUR", theme: "system", payday: 0, plannedSavings: 0, reminders: true },
    accounts: [],
    categories: [
      ...["Groceries", "Eating out", "Transport", "Housing", "Utilities", "Shopping", "Entertainment", "Health", "Travel", "Education", "Other"].map((name, index) => ({ id: uid(), name, kind: "expense", icon: categoryIcons[name], color: index })),
      ...["Salary", "Gifts", "Interest", "Other income"].map((name, index) => ({ id: uid(), name, kind: "income", icon: categoryIcons[name] || "＋", color: index + 3 })),
    ],
    transactions: [], budgets: [], goals: [], goalEntries: [], bills: [], debts: [], debtEntries: [],
  };
}

function demoData() {
  const data = emptyData();
  const current = new Date();
  const iso = offset => {
    const date = new Date(current);
    date.setDate(date.getDate() + offset);
    return isoDate(date);
  };
  const everyday = { id: uid(), name: "Everyday account", type: "current", opening: 742065, includeSpendable: true };
  const savings = { id: uid(), name: "Savings", type: "savings", opening: 520000, includeSpendable: false };
  const cash = { id: uid(), name: "Cash wallet", type: "cash", opening: 4200, includeSpendable: true };
  const card = { id: uid(), name: "Credit card", type: "credit", opening: -31400, includeSpendable: true };
  data.accounts = [everyday, savings, cash, card];
  const cat = name => data.categories.find(item => item.name === name)?.id;
  data.transactions = [
    { id: uid(), type: "income", amount: 325000, date: iso(-19), categoryId: cat("Salary"), accountId: everyday.id, description: "Acme salary", method: "Bank transfer", notes: "" },
    { id: uid(), type: "expense", amount: 4280, date: iso(0), categoryId: cat("Groceries"), accountId: everyday.id, description: "Fresh Market", method: "Card", notes: "" },
    { id: uid(), type: "expense", amount: 640, date: iso(0), categoryId: cat("Eating out"), accountId: everyday.id, description: "Café Central", method: "Card", notes: "" },
    { id: uid(), type: "expense", amount: 8620, date: iso(-2), categoryId: cat("Utilities"), accountId: everyday.id, description: "Green Energy", method: "Direct debit", notes: "" },
    { id: uid(), type: "expense", amount: 12900, date: iso(-5), categoryId: cat("Transport"), accountId: card.id, description: "Rail pass", method: "Card", notes: "" },
    { id: uid(), type: "transfer", amount: 35000, date: iso(-8), accountId: everyday.id, toAccountId: savings.id, description: "Monthly savings", method: "Bank transfer", notes: "" },
  ];
  data.budgets = [
    { id: uid(), categoryId: null, amount: 250000 },
    { id: uid(), categoryId: cat("Groceries"), amount: 45000 },
    { id: uid(), categoryId: cat("Eating out"), amount: 16000 },
    { id: uid(), categoryId: cat("Transport"), amount: 22000 },
  ];
  data.goals = [
    { id: uid(), name: "Emergency fund", target: 900000, initial: 520000, targetDate: iso(365), icon: "🛟", archived: false },
    { id: uid(), name: "Portugal", target: 300000, initial: 156000, targetDate: iso(240), icon: "🏖️", archived: false },
    { id: uid(), name: "New laptop", target: 250000, initial: 100000, targetDate: iso(430), icon: "💻", archived: false },
  ];
  data.bills = [
    { id: uid(), name: "Rent", amount: 98000, frequency: "Monthly", dueDate: iso(4), categoryId: cat("Housing"), accountId: everyday.id, subscription: false, active: true },
    { id: uid(), name: "Netflix", amount: 1399, frequency: "Monthly", dueDate: iso(7), categoryId: cat("Entertainment"), accountId: card.id, subscription: true, active: true },
    { id: uid(), name: "Phone plan", amount: 2499, frequency: "Monthly", dueDate: iso(10), categoryId: cat("Utilities"), accountId: everyday.id, subscription: true, active: true },
  ];
  data.debts = [
    { id: uid(), name: "Car loan", kind: "loan", counterparty: "Community Bank", principal: 1200000, rateBps: 525, startDate: iso(-390), monthlyPayment: 26000, accountId: everyday.id, closed: false },
  ];
  data.debtEntries = [{ id: uid(), debtId: data.debts[0].id, kind: "repayment", date: iso(-30), principal: 21000, interest: 5000 }];
  data.settings.plannedSavings = 35000;
  return data;
}

function loadData() {
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    return raw ? JSON.parse(raw) : null;
  } catch {
    return null;
  }
}
let state = demoMode ? demoData() : loadData();
let route = "dashboard";
let transactionQuery = "";
let transactionType = "all";
let reportPeriod = "month";
let reportOffset = 0;

function save() {
  if (demoMode) return true;
  try {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(state));
  } catch {
    state = loadData();
    closeModal();
    render();
    alert("This change was not saved because MyMoney's storage on this device is full. Remove some receipt photos, or create a backup and delete old data, then try again.");
    return false;
  }
  if (!save.persistenceRequested) {
    save.persistenceRequested = true;
    navigator.storage?.persist?.().catch(() => {});
  }
  return true;
}
function enterDemo() {
  demoMode = true;
  state = demoData();
  route = "dashboard";
  $("#welcome")?.remove();
  render();
}
function exitDemo() {
  demoMode = false;
  history.replaceState(null, "", location.pathname);
  state = loadData();
  route = "dashboard";
  render();
}
function showToast(message) {
  const toast = $("#toast");
  toast.textContent = message;
  toast.classList.add("show");
  clearTimeout(showToast.timer);
  showToast.timer = setTimeout(() => toast.classList.remove("show"), 1800);
}
function accountBalance(accountId) {
  const account = state.accounts.find(item => item.id === accountId);
  if (!account) return 0;
  return state.transactions.reduce((balance, tx) => {
    if (tx.accountId === accountId) {
      if (tx.type === "income" || tx.type === "debt_in") balance += tx.amount;
      else balance -= tx.amount;
    }
    if (tx.type === "transfer" && tx.toAccountId === accountId) balance += tx.amount;
    return balance;
  }, account.opening);
}
const totalBalance = () => state.accounts.reduce((sum, account) => sum + accountBalance(account.id), 0);
const categoryById = id => state.categories.find(item => item.id === id);
const accountById = id => state.accounts.find(item => item.id === id);
const transactionsInMonth = key => state.transactions.filter(tx => tx.date.startsWith(key));
const expenseTotal = list => list.filter(tx => tx.type === "expense").reduce((sum, tx) => sum + tx.amount, 0);
const incomeTotal = list => list.filter(tx => tx.type === "income").reduce((sum, tx) => sum + tx.amount, 0);
const goalSaved = goal => goal.initial + state.goalEntries.filter(entry => entry.goalId === goal.id).reduce((sum, entry) => sum + entry.amount, 0);
const debtOutstanding = debt => Math.max(0, debt.principal + state.debtEntries.filter(entry => entry.debtId === debt.id).reduce((sum, entry) => sum + (entry.kind === "increase" ? entry.principal : -entry.principal), 0));
const currentMonthTransactions = () => transactionsInMonth(monthKey());

function safeToSpend() {
  const today = new Date(`${todayIso()}T12:00:00`);
  let end;
  if (state.settings.payday > 0) {
    const payday = new Date(today.getFullYear(), today.getMonth(), Math.min(state.settings.payday, new Date(today.getFullYear(), today.getMonth() + 1, 0).getDate()), 12);
    if (payday <= today) payday.setMonth(payday.getMonth() + 1);
    end = new Date(payday);
    end.setDate(end.getDate() - 1);
  } else {
    end = new Date(today.getFullYear(), today.getMonth() + 1, 0, 12);
  }
  const days = Math.max(1, Math.round((end - today) / 86400000) + 1);
  const spendable = state.accounts.filter(account => account.includeSpendable).reduce((sum, account) => sum + accountBalance(account.id), 0);
  const bills = state.bills.filter(bill => bill.active && new Date(`${bill.dueDate}T12:00:00`) <= end).reduce((sum, bill) => sum + bill.amount, 0);
  const saved = currentMonthTransactions().filter(tx => tx.type === "transfer" && accountById(tx.toAccountId)?.type === "savings").reduce((sum, tx) => sum + tx.amount, 0);
  const savingsReserve = Math.max(0, state.settings.plannedSavings - saved);
  const overall = state.budgets.find(budget => budget.categoryId === null)?.amount;
  const budgetRemaining = overall == null ? null : overall - expenseTotal(currentMonthTransactions());
  const available = spendable - bills - savingsReserve;
  const safe = budgetRemaining == null ? available : Math.min(available, budgetRemaining);
  return { total: safe, daily: Math.max(0, Math.floor(safe / days)), days, bills, savingsReserve, end: isoDate(end) };
}

function renderNav() {
  $("#mainNav").innerHTML = navItems.map(item => item.length === 1
    ? `<div class="nav-section">${item[0]}</div>`
    : `<button class="nav-item ${route === item[0] ? "active" : ""}" data-route="${item[0]}" type="button"><span class="nav-icon">${item[1]}</span><span>${item[2]}</span></button>`
  ).join("");
}

function navigate(next) {
  route = next;
  document.body.classList.remove("menu-open");
  render();
}

function render() {
  if (!state) {
    renderWelcome();
    return;
  }
  document.body.classList.toggle("dark", state.settings.theme === "dark" || (state.settings.theme === "system" && matchMedia("(prefers-color-scheme: dark)").matches));
  renderNav();
  const [eyebrow, title] = pageMeta[route] || pageMeta.dashboard;
  $("#pageEyebrow").textContent = eyebrow;
  $("#pageTitle").textContent = title;
  const dark = document.body.classList.contains("dark");
  $("#themeToggle").innerHTML = dark ? themeIcons.sun : themeIcons.moon;
  $("#themeToggle").setAttribute("aria-label", dark ? "Switch to light mode" : "Switch to dark mode");
  $("#themeToggle").title = dark ? "Light mode" : "Dark mode";
  const renderers = { dashboard: renderDashboard, transactions: renderTransactions, accounts: renderAccounts, budgets: renderBudgets, goals: renderGoals, bills: renderBills, debts: renderDebts, planning: renderPlanning, reports: renderReports, settings: renderSettings };
  const demoBanner = demoMode ? `<div class="demo-banner"><span><strong>Sample data.</strong> Explore freely — nothing here is saved.</span><button class="demo-exit" id="exitDemo" type="button">Start with my own money</button></div>` : "";
  $("#content").innerHTML = demoBanner + (renderers[route] || renderDashboard)();
  bindPage();
}

function renderWelcome() {
  if ($("#welcome")) return;
  document.body.insertAdjacentHTML("beforeend", `
    <section class="welcome" id="welcome">
      <div class="welcome-card">
        <div class="welcome-mark">M</div>
        <h1>Your money,<br>quietly in control.</h1>
        <p>MyMoney is a private finance app that works offline. No login, no ads, no cloud — your data stays on this device.</p>
        <div class="welcome-features">
          <div class="welcome-feature"><strong>Safe to spend</strong><small>Know your daily allowance after bills and savings.</small></div>
          <div class="welcome-feature"><strong>Plan ahead</strong><small>Budgets, goals, debts and forecasts in one place.</small></div>
          <div class="welcome-feature"><strong>Your data</strong><small>Encrypted backups and offline exports.</small></div>
        </div>
        <div class="welcome-actions">
          <button class="primary-button" id="startFresh" type="button">Set up MyMoney</button>
          <button class="secondary-button" id="exploreDemo" type="button">Explore with sample data</button>
        </div>
      </div>
    </section>`);
  $("#exploreDemo").onclick = enterDemo;
  $("#startFresh").onclick = () => setupModal();
}

function sectionHeader(title, action = "", actionId = "") {
  return `<div class="section-header"><h2>${title}</h2>${action ? `<button class="link-button" id="${actionId}" type="button">${action}</button>` : ""}</div>`;
}
function metric(icon, label, value, change = "", tone = "") {
  return `<section class="card metric-card"><div class="metric-top"><small>${label}</small><span class="metric-icon">${icon}</span></div><strong class="money ${tone}">${value}</strong>${change ? `<span class="metric-change ${tone}">${change}</span>` : ""}</section>`;
}
function renderBars(months) {
  const max = Math.max(1, ...months.flatMap(item => [item.income, item.expense]));
  return `<div class="chart"><div class="bar-chart">${months.map(item => `<div class="bar-group"><i class="bar" style="height:${Math.max(2, item.income * 100 / max)}%"></i><i class="bar expense" style="height:${Math.max(2, item.expense * 100 / max)}%"></i><span class="bar-name">${escapeHtml(item.label)}</span></div>`).join("")}</div></div><div class="legend"><span><i class="legend-dot"></i>Income</span><span><i class="legend-dot expense"></i>Expenses</span></div>`;
}
function recentTable(items, limit = items.length) {
  if (!items.length) return emptyState("↕", "No transactions yet", "Add your first income or expense to see your activity.");
  return `<table class="data-table"><thead><tr><th>Transaction</th><th>Date</th><th>Account</th><th>Amount</th></tr></thead><tbody>${items.slice(0, limit).map(txRow).join("")}</tbody></table>`;
}
function txRow(tx) {
  const cat = categoryById(tx.categoryId);
  const title = tx.description || (tx.type === "transfer" ? "Transfer" : cat?.name || "Transaction");
  const subtitle = tx.type === "transfer" ? `${accountById(tx.accountId)?.name || "Account"} → ${accountById(tx.toAccountId)?.name || "Account"}` : cat?.name || tx.type.replace("_", " ");
  const signed = tx.type === "expense" || tx.type === "debt_out" ? -tx.amount : tx.amount;
  return `<tr data-edit-tx="${tx.id}"><td><span class="table-icon">${cat?.icon || (tx.type === "transfer" ? "⇄" : "•")}</span><span class="table-title"><strong>${escapeHtml(title)}</strong><small>${escapeHtml(subtitle)}</small></span></td><td>${dateLabel(tx.date)}</td><td>${escapeHtml(accountById(tx.accountId)?.name || "—")}</td><td class="money ${signed < 0 ? "negative" : tx.type === "income" ? "positive" : ""}">${money(signed, true)}</td></tr>`;
}
function emptyState(icon, title, text, action = "", id = "") {
  return `<div class="empty-state"><div class="empty-icon">${icon}</div><h3>${title}</h3><p>${text}</p>${action ? `<button class="primary-button" id="${id}" type="button">${action}</button>` : ""}</div>`;
}

function renderDashboard() {
  const txs = currentMonthTransactions();
  const income = incomeTotal(txs), expense = expenseTotal(txs), net = income - expense;
  const safe = safeToSpend();
  const recent = [...state.transactions].sort((a, b) => b.date.localeCompare(a.date));
  const months = Array.from({ length: 6 }, (_, index) => {
    const date = new Date();
    date.setMonth(date.getMonth() - (5 - index));
    const key = `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, "0")}`;
    const monthTxs = transactionsInMonth(key);
    return { label: date.toLocaleDateString(dateLocale, { month: "short" }), income: incomeTotal(monthTxs), expense: expenseTotal(monthTxs) };
  });
  const upcoming = state.bills.filter(bill => bill.active).sort((a, b) => a.dueDate.localeCompare(b.dueDate)).slice(0, 4);
  return `${dashboardNotices()}
    <div class="grid main">
      <div class="stack">
        <section class="card hero-card">
          <span class="hero-label">Total money across ${plural(state.accounts.length, "account")}</span>
          <strong class="hero-money money">${money(totalBalance())}</strong>
          <div class="hero-stats">
            <div class="hero-stat"><small>Income this month</small><strong class="money">${money(income)}</strong></div>
            <div class="hero-stat"><small>Spent this month</small><strong class="money">${money(expense)}</strong></div>
            <div class="hero-stat"><small>Net savings</small><strong class="money">${money(net, true)}</strong></div>
          </div>
        </section>
        <div class="grid cols-3">
          ${metric("↙", "Monthly income", money(income), income ? "Money received" : "No income yet", "positive")}
          ${metric("↗", "Monthly expenses", money(expense), `${txs.filter(tx => tx.type === "expense").length} payments`, "negative")}
          ${metric("◇", "Savings rate", income > 0 ? `${percent(net, income)}%` : "—", net >= 0 ? "On the right track" : "Spending exceeds income", net >= 0 ? "positive" : "negative")}
        </div>
        <div>
          ${sectionHeader("Income vs expenses", "Open reports", "openReports")}
          <section class="card">${renderBars(months)}</section>
        </div>
        <div>
          ${sectionHeader("Recent activity", "See all", "seeAllTransactions")}
          <section class="card" style="padding:5px 8px">${recentTable(recent, 5)}</section>
        </div>
      </div>
      <aside class="stack">
        <div>
          ${sectionHeader("Safe to spend", "Edit plan", "editPlan")}
          <section class="card safe-card">
            <div><small class="muted">Per day until ${dateLabel(safe.end)}</small><strong class="money">${money(safe.daily)}</strong><small class="muted">${money(Math.max(0, safe.total))} available after bills and planned savings</small></div>
            <div class="ring" style="--value:${clamp(safe.days / 31 * 100, 4, 100)}%" data-label="${plural(safe.days, "day")}"></div>
          </section>
        </div>
        <div>
          ${sectionHeader("Upcoming payments", "View bills", "openBills")}
          <section class="card">${upcoming.length ? upcoming.map(bill => `<div class="bill-row"><span class="bill-symbol">${bill.subscription ? "⟳" : "▤"}</span><div class="bill-copy"><strong>${escapeHtml(bill.name)}</strong><small>${dateLabel(bill.dueDate)}</small></div><strong class="money">${money(bill.amount)}</strong></div>`).join("") : emptyState("▤", "No upcoming bills", "Add bills to reserve them from safe-to-spend.")}</section>
        </div>
        <div>
          ${sectionHeader("Goal progress", "View goals", "openGoals")}
          <section class="card">${state.goals.filter(goal => !goal.archived).slice(0, 3).map(goal => { const saved = goalSaved(goal), p = clamp(percent(saved, goal.target), 0, 100); return `<div class="progress-row"><div class="progress-head"><strong>${goal.icon} ${escapeHtml(goal.name)}</strong><span>${p}%</span></div><div class="progress-track"><i class="progress-fill" style="width:${p}%"></i></div></div>`; }).join("") || `<p class="muted">Create a savings goal to track your progress.</p>`}</section>
        </div>
      </aside>
    </div>`;
}

function dashboardNotices() {
  const notices = [];
  if (installPrompt) notices.push(notice("⤓", "Install MyMoney on this device", "Open it from your home screen or Start menu, even offline.", `<button class="primary-button" id="installNow" type="button">Install</button>`));
  const backupDue = !demoMode && state.transactions.length >= 10 && (!state.settings.lastBackup || daysUntil(state.settings.lastBackup) < -30);
  if (backupDue) notices.push(notice("🔒", "Back up your money data", "It is stored only on this device. Save an encrypted copy somewhere safe.", `<button class="secondary-button" id="backupNow" type="button">Back up now</button>`));
  return notices.join("");
}
function notice(icon, title, text, action) {
  return `<div class="notice"><span class="notice-icon">${icon}</span><div class="notice-copy"><strong>${title}</strong><small>${text}</small></div>${action}</div>`;
}

function renderTransactions() {
  const filtered = [...state.transactions]
    .filter(tx => transactionType === "all" || tx.type === transactionType)
    .filter(tx => {
      const text = `${tx.description} ${categoryById(tx.categoryId)?.name || ""} ${accountById(tx.accountId)?.name || ""}`.toLowerCase();
      return text.includes(transactionQuery.toLowerCase());
    }).sort((a, b) => b.date.localeCompare(a.date));
  const income = incomeTotal(filtered), expense = expenseTotal(filtered);
  return `
    <div class="grid cols-3" style="margin-bottom:18px">
      ${metric("↙", "Money in", money(income), plural(filtered.filter(tx => tx.type === "income").length, "entry", "entries"), "positive")}
      ${metric("↗", "Money out", money(expense), plural(filtered.filter(tx => tx.type === "expense").length, "entry", "entries"), "negative")}
      ${metric("⇄", "Net", money(income - expense, true), "Transfers excluded", income >= expense ? "positive" : "negative")}
    </div>
    <div class="toolbar">
      <input id="txSearch" class="search-input" value="${escapeHtml(transactionQuery)}" placeholder="Search description, category or account…" />
      <select id="txType" class="filter-select">
        ${[["all", "All types"], ["expense", "Expenses"], ["income", "Income"], ["transfer", "Transfers"]].map(([value, label]) => `<option value="${value}" ${transactionType === value ? "selected" : ""}>${label}</option>`).join("")}
      </select>
      <button class="secondary-button" id="exportTxCsv" type="button">Export CSV</button>
    </div>
    <section class="card" style="padding:5px 8px">${recentTable(filtered)}</section>`;
}

function renderAccounts() {
  return `
    <div class="toolbar"><button class="primary-button" id="newAccount" type="button">＋ New account</button><button class="secondary-button" id="newTransfer" type="button">⇄ Transfer money</button></div>
    ${state.accounts.length ? `<div class="grid cols-3">${state.accounts.map(account => `<section class="card account-card clickable" data-edit-account="${account.id}"><div class="account-head"><span class="account-symbol">${account.type === "savings" ? "◇" : account.type === "cash" ? "¤" : account.type === "credit" ? "▭" : "▣"}</span><div class="account-copy"><strong>${escapeHtml(account.name)}</strong><small>${({ current: "Current account", savings: "Savings account", cash: "Cash wallet", credit: "Credit card", other: "Other account" })[account.type]}</small></div><span class="muted">›</span></div><div class="account-balance money ${accountBalance(account.id) < 0 ? "negative" : ""}">${money(accountBalance(account.id))}</div><small class="muted">${account.includeSpendable ? "Included in safe-to-spend" : "Protected from safe-to-spend"}</small></section>`).join("")}</div>` : emptyState("▣", "Add your first account", "Start with your main bank account and its current balance.", "Add account", "newAccountEmpty")}`;
}

function renderBudgets() {
  const txs = currentMonthTransactions();
  const expense = expenseTotal(txs);
  const overall = state.budgets.find(budget => budget.categoryId === null);
  const safe = safeToSpend();
  const lines = state.budgets.filter(budget => budget.categoryId).map(budget => {
    const category = categoryById(budget.categoryId);
    const spent = txs.filter(tx => tx.type === "expense" && tx.categoryId === budget.categoryId).reduce((sum, tx) => sum + tx.amount, 0);
    return { budget, category, spent };
  });
  return `
    <div class="grid cols-3" style="margin-bottom:18px">
      ${metric("◎", "Safe per day", money(safe.daily), `${plural(safe.days, "day")} remaining`, "positive")}
      ${metric("▥", "Upcoming bills", money(safe.bills), "Reserved before you spend")}
      ${metric("◇", "Planned savings", money(safe.savingsReserve), "Still to set aside")}
    </div>
    <div class="grid main">
      <div class="stack">
        ${sectionHeader("Monthly budget", overall ? "Edit overall" : "Set overall budget", "editOverallBudget")}
        <section class="card">${overall ? budgetProgress("Overall spending", expense, overall.amount) : emptyState("◔", "No overall budget", "Set a monthly spending limit to cap your safe-to-spend allowance.", "Set budget", "setOverallEmpty")}</section>
        ${sectionHeader("Category budgets", "Add category budget", "addCategoryBudget")}
        <section class="card">${lines.length ? lines.map(line => `<div class="clickable" data-edit-budget="${line.budget.id}">${budgetProgress(`${line.category?.icon || "•"} ${escapeHtml(line.category?.name || "Category")}`, line.spent, line.budget.amount)}</div>`).join("") : emptyState("◎", "No category budgets", "Set limits for groceries, eating out or any spending category.")}</section>
      </div>
      <aside class="stack">
        ${sectionHeader("Your plan", "Edit", "editPlan")}
        <section class="card">
          <div class="progress-head"><span>Spendable balance</span><strong>${money(state.accounts.filter(account => account.includeSpendable).reduce((sum, account) => sum + accountBalance(account.id), 0))}</strong></div>
          <div class="progress-head"><span>Upcoming bills</span><strong>${money(-safe.bills)}</strong></div>
          <div class="progress-head"><span>Savings reserve</span><strong>${money(-safe.savingsReserve)}</strong></div>
          <hr style="border:0;border-top:1px solid var(--line);margin:12px 0">
          <div class="progress-head"><strong>Safe until ${dateLabel(safe.end)}</strong><strong class="${safe.total < 0 ? "negative" : "positive"}">${money(Math.max(0, safe.total))}</strong></div>
        </section>
        <div class="assumption">Transfers, debt principal and savings-goal allocations are not counted as spending. Upcoming active bills are reserved until payday or month end.</div>
      </aside>
    </div>`;
}
function budgetProgress(label, spent, budget) {
  const p = clamp(percent(spent, budget), 0, 100);
  const tone = spent > budget ? "danger" : p >= 85 ? "warn" : "";
  return `<div class="progress-row"><div class="progress-head"><strong>${label}</strong><span>${money(spent)} of ${money(budget)}</span></div><div class="progress-track"><i class="progress-fill ${tone}" style="width:${p}%"></i></div><small class="${spent > budget ? "negative" : "muted"}">${spent > budget ? `${money(spent - budget)} over budget` : `${money(budget - spent)} remaining`}</small></div>`;
}

function renderGoals() {
  const active = state.goals.filter(goal => !goal.archived);
  const totalSaved = active.reduce((sum, goal) => sum + goalSaved(goal), 0);
  const totalTarget = active.reduce((sum, goal) => sum + goal.target, 0);
  return `
    <div class="grid cols-3" style="margin-bottom:18px">
      ${metric("◇", "Saved toward goals", money(totalSaved), plural(active.length, "active goal"), "positive")}
      ${metric("◎", "Combined target", money(totalTarget), totalTarget ? `${percent(totalSaved, totalTarget)}% complete` : "Create your first goal")}
      ${metric("↗", "Remaining", money(Math.max(0, totalTarget - totalSaved)), "Across active goals")}
    </div>
    <div class="toolbar"><button class="primary-button" id="newGoal" type="button">＋ New savings goal</button></div>
    ${active.length ? `<div class="grid cols-3">${active.map(goal => { const saved = goalSaved(goal), p = clamp(percent(saved, goal.target), 0, 100), months = goal.targetDate ? Math.max(1, Math.ceil((new Date(`${goal.targetDate}T12:00:00`) - new Date()) / (86400000 * 30))) : null, required = months ? Math.ceil(Math.max(0, goal.target - saved) / months) : null; return `<section class="card goal-card clickable" data-goal="${goal.id}"><div class="goal-head"><span class="goal-symbol">${goal.icon || "◇"}</span><div class="goal-copy"><strong>${escapeHtml(goal.name)}</strong><small>${goal.targetDate ? `Target ${dateLabel(goal.targetDate)}` : "No target date"}</small></div><strong class="positive">${p}%</strong></div><div class="goal-amount money">${money(saved)} <small class="muted">of ${money(goal.target)}</small></div><div class="progress-track"><i class="progress-fill" style="width:${p}%"></i></div><p class="muted" style="font-size:10px;margin-bottom:0">${required != null ? `${money(required)}/month needed` : `${money(Math.max(0, goal.target - saved))} to go`}</p></section>`; }).join("")}</div>` : emptyState("◇", "Create your first savings goal", "Set a target, add contributions and MyMoney will show the monthly amount you need.", "New goal", "newGoalEmpty")}`;
}

function renderBills() {
  const active = [...state.bills].filter(bill => bill.active).sort((a, b) => a.dueDate.localeCompare(b.dueDate));
  const monthly = active.reduce((sum, bill) => sum + monthlyEquivalent(bill.amount, bill.frequency), 0);
  const subscriptions = active.filter(bill => bill.subscription).reduce((sum, bill) => sum + monthlyEquivalent(bill.amount, bill.frequency), 0);
  return `
    <div class="grid cols-3" style="margin-bottom:18px">
      ${metric("▤", "Monthly equivalent", money(monthly), plural(active.length, "active payment"))}
      ${metric("⟳", "Subscriptions", money(subscriptions), plural(active.filter(bill => bill.subscription).length, "recurring service"))}
      ${metric("!", "Due in 30 days", money(active.filter(bill => daysUntil(bill.dueDate) <= 30).reduce((sum, bill) => sum + bill.amount, 0)), "Nothing is deducted automatically", "warning")}
    </div>
    <div class="toolbar"><button class="primary-button" id="newBill" type="button">＋ New bill</button></div>
    <section class="card">${active.length ? active.map(bill => `<div class="bill-row"><span class="bill-symbol">${bill.subscription ? "⟳" : "▤"}</span><div class="bill-copy"><strong>${escapeHtml(bill.name)}</strong><small>${bill.frequency} · ${bill.dueDate < todayIso() ? "Overdue " : "Due "}${dateLabel(bill.dueDate)}</small></div><strong class="money">${money(bill.amount)}</strong><div class="row-actions"><button class="tiny-button primary" data-pay-bill="${bill.id}" type="button">Mark paid</button><button class="tiny-button" data-edit-bill="${bill.id}" type="button">Edit</button></div></div>`).join("") : emptyState("▤", "No bills yet", "Add rent, utilities and subscriptions. MyMoney reserves upcoming amounts without deducting them.", "Add bill", "newBillEmpty")}</section>`;
}
const daysUntil = date => Math.ceil((new Date(`${date}T12:00:00`) - new Date(`${todayIso()}T12:00:00`)) / 86400000);
function monthlyEquivalent(amount, frequency) {
  if (frequency === "Weekly") return Math.round(amount * 52 / 12);
  if (frequency === "Every 2 weeks") return Math.round(amount * 26 / 12);
  if (frequency === "Quarterly") return Math.round(amount / 3);
  if (frequency === "Yearly") return Math.round(amount / 12);
  return amount;
}

function renderDebts() {
  const open = state.debts.filter(debt => !debt.closed);
  const owed = open.filter(debt => debt.kind !== "lent").reduce((sum, debt) => sum + debtOutstanding(debt), 0);
  const owedToMe = open.filter(debt => debt.kind === "lent").reduce((sum, debt) => sum + debtOutstanding(debt), 0);
  return `
    <div class="grid cols-3" style="margin-bottom:18px">
      ${metric("↗", "You owe", money(owed), "Principal outstanding", "negative")}
      ${metric("↙", "Owed to you", money(owedToMe), "Money lent", "positive")}
      ${metric("◎", "Monthly repayments", money(open.filter(debt => debt.kind !== "lent").reduce((sum, debt) => sum + debt.monthlyPayment, 0)), "Current repayment plans")}
    </div>
    <div class="toolbar"><button class="primary-button" id="newDebt" type="button">＋ Add debt or loan</button></div>
    <section class="card">${open.length ? open.map(debt => { const outstanding = debtOutstanding(debt), repaid = Math.max(0, debt.principal - outstanding), p = clamp(percent(repaid, debt.principal), 0, 100); return `<div class="debt-row"><span class="debt-symbol">${debt.kind === "lent" ? "↙" : debt.kind === "borrowed" ? "↗" : "⚖"}</span><div class="debt-copy"><strong>${escapeHtml(debt.name)}</strong><small>${escapeHtml(debt.counterparty || debt.kind)} · ${(debt.rateBps / 100).toFixed(2)}% APR</small><div class="progress-track" style="margin-top:7px"><i class="progress-fill" style="width:${p}%"></i></div></div><strong class="money">${money(outstanding)}</strong><div class="row-actions"><button class="tiny-button primary" data-pay-debt="${debt.id}" type="button">${debt.kind === "lent" ? "Payment received" : "Repay"}</button><button class="tiny-button" data-edit-debt="${debt.id}" type="button">Edit</button></div></div>`; }).join("") : emptyState("⚖", "No debts tracked", "Track loans, money borrowed, or money someone owes you.", "Add debt", "newDebtEmpty")}</section>
    <div class="assumption" style="margin-top:18px">Principal movements are not income or spending. Only interest is included in your expense or income reports.</div>`;
}

function renderPlanning() {
  const lastThree = Array.from({ length: 3 }, (_, index) => {
    const date = new Date(); date.setMonth(date.getMonth() - (index + 1));
    return transactionsInMonth(`${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, "0")}`);
  }).flat();
  const avgIncome = lastThree.length ? Math.floor(incomeTotal(lastThree) / 3) : incomeTotal(currentMonthTransactions());
  const avgExpense = lastThree.length ? Math.floor(expenseTotal(lastThree) / 3) : expenseTotal(currentMonthTransactions());
  return `
    <div class="forecast-grid">
      <section class="card">
        <h2 style="margin-top:0">Try a scenario</h2>
        <p class="muted">Pre-filled with your recent averages. Change any number.</p>
        <div class="form-grid" id="plannerForm">
          ${field("planIncome", "Monthly income", amountInput(avgIncome), "text")}
          ${field("planExpense", "Monthly spending", amountInput(avgExpense), "text")}
          ${field("planStart", "Starting savings", amountInput(Math.max(0, totalBalance())), "text")}
          ${field("planRate", "Annual return (%)", "0", "number", 'step="0.01" min="0" max="100"')}
          ${field("planReduce", "Reduce spending (%)", "10", "number", 'step="1" min="0" max="80"')}
          ${field("planTarget", "Savings target", "20000.00", "text")}
        </div>
        <button class="primary-button" id="calculatePlan" type="button" style="margin-top:15px">Calculate forecast</button>
        <div class="assumption" style="margin-top:14px">Assumes income and spending stay constant, returns compound monthly, and excludes inflation, tax and one-off costs.</div>
      </section>
      <section class="card" id="forecastOutput">${forecastOutput(avgIncome, avgExpense, Math.max(0, totalBalance()), 0, 10, 2000000)}</section>
    </div>`;
}
function forecastValues(start, monthly, months, rateBps) {
  const values = [start];
  let balance = start;
  for (let index = 0; index < months; index++) {
    const interest = Math.round(balance * rateBps / 120000);
    balance += interest + monthly;
    values.push(balance);
  }
  return values;
}
function forecastOutput(income, expense, start, rateBps, reduce, target) {
  const surplus = income - expense;
  const reducedExpense = Math.round(expense * (100 - reduce) / 100);
  const improved = income - reducedExpense;
  const normal = forecastValues(start, surplus, 12, rateBps);
  const better = forecastValues(start, improved, 12, rateBps);
  const all = [...normal, ...better], min = Math.min(...all), max = Math.max(...all), range = Math.max(1, max - min);
  const points = values => values.map((value, index) => `${20 + index * 46.5},${225 - (value - min) * 180 / range}`).join(" ");
  let months = 0, balance = start;
  while (balance < target && months < 1200 && (surplus > 0 || rateBps > 0)) {
    balance += Math.round(balance * rateBps / 120000) + surplus; months++;
  }
  return `<h2 style="margin-top:0">12-month forecast</h2>
    <div class="forecast-result">
      <div class="forecast-stat"><small>In 6 months</small><strong>${money(normal[6])}</strong></div>
      <div class="forecast-stat"><small>In 12 months</small><strong>${money(normal[12])}</strong></div>
      <div class="forecast-stat"><small>With ${reduce}% less spending</small><strong class="positive">${money(better[12])}</strong></div>
    </div>
    <svg class="line-chart" viewBox="0 0 600 260" role="img" aria-label="Savings forecast chart">
      ${[45, 105, 165, 225].map(y => `<line class="gridline" x1="20" y1="${y}" x2="578" y2="${y}"/>`).join("")}
      <polyline class="line" points="${points(normal)}"/><polyline class="line alt" points="${points(better)}"/>
      ${[0, 3, 6, 9, 12].map(i => `<text x="${20 + i * 46.5}" y="248" text-anchor="middle">${i === 0 ? "Now" : `${i}m`}</text>`).join("")}
    </svg>
    <div class="legend"><span><i class="legend-dot" style="background:var(--primary)"></i>Current plan</span><span><i class="legend-dot"></i>Spending reduced</span></div>
    <p><strong>Time to ${money(target)}:</strong> ${balance >= target ? (months === 0 ? "Already there" : plural(months, "month")) : "Not reachable with this monthly plan"}</p>`;
}

function reportRange() {
  const anchor = new Date();
  if (reportPeriod === "week") {
    anchor.setDate(anchor.getDate() + reportOffset * 7);
    const day = anchor.getDay() || 7;
    const start = new Date(anchor); start.setDate(anchor.getDate() - day + 1);
    const end = new Date(start); end.setDate(start.getDate() + 6);
    return [isoDate(start), isoDate(end), `${dateLabel(isoDate(start))} – ${dateLabel(isoDate(end))}`];
  }
  if (reportPeriod === "year") {
    const year = anchor.getFullYear() + reportOffset;
    return [`${year}-01-01`, `${year}-12-31`, String(year)];
  }
  anchor.setMonth(anchor.getMonth() + reportOffset);
  const key = `${anchor.getFullYear()}-${String(anchor.getMonth() + 1).padStart(2, "0")}`;
  const end = new Date(anchor.getFullYear(), anchor.getMonth() + 1, 0).getDate();
  return [`${key}-01`, `${key}-${end}`, monthLabel(key)];
}
function reportData() {
  const [start, end, label] = reportRange();
  const txs = state.transactions.filter(tx => tx.date >= start && tx.date <= end);
  const expense = expenseTotal(txs), income = incomeTotal(txs);
  const byCategory = state.categories.filter(cat => cat.kind === "expense").map(cat => ({ category: cat, total: txs.filter(tx => tx.type === "expense" && tx.categoryId === cat.id).reduce((sum, tx) => sum + tx.amount, 0) })).filter(item => item.total > 0).sort((a, b) => b.total - a.total);
  return { start, end, label, txs, expense, income, net: income - expense, byCategory };
}
function renderReports() {
  const report = reportData();
  const top = report.byCategory.slice(0, 4);
  const palette = ["var(--primary)", "var(--blue)", "var(--warning)", "var(--violet)"];
  return `
    <div class="toolbar no-print">
      <div class="tabs">${[["week", "Week"], ["month", "Month"], ["year", "Year"]].map(([value, label]) => `<button class="tab ${reportPeriod === value ? "active" : ""}" data-report-period="${value}" type="button">${label}</button>`).join("")}</div>
      <button class="secondary-button" id="reportPrev" type="button">‹ Previous</button>
      <strong style="min-width:150px;text-align:center">${report.label}</strong>
      <button class="secondary-button" id="reportNext" type="button" ${reportOffset >= 0 ? "disabled" : ""}>Next ›</button>
      <span style="flex:1"></span>
      <button class="secondary-button" id="reportCsv" type="button">Export CSV</button>
      <button class="primary-button" id="reportPrint" type="button">Print / Save PDF</button>
    </div>
    <div class="grid cols-4" style="margin-bottom:18px">
      ${metric("↙", "Income", money(report.income), report.label, "positive")}
      ${metric("↗", "Expenses", money(report.expense), report.label, "negative")}
      ${metric("◇", "Net savings", money(report.net, true), report.income ? `${percent(report.net, report.income)}% savings rate` : "No income", report.net >= 0 ? "positive" : "negative")}
      ${metric("÷", "Average per day", money(Math.floor(report.expense / Math.max(1, Math.round((new Date(`${report.end}T12:00:00`) - new Date(`${report.start}T12:00:00`)) / 86400000) + 1))), "Expense average")}
    </div>
    <div class="grid cols-2">
      <section class="card"><h2 style="margin-top:0">Spending breakdown</h2>${top.length ? `<div class="donut-wrap"><div class="donut" data-center="${money(report.expense)}&#10;spent"></div><div class="category-legend">${top.map((item, index) => `<div class="category-line"><i style="background:${palette[index]}"></i><span>${item.category.icon} ${escapeHtml(item.category.name)}</span><strong>${money(item.total)}</strong></div>`).join("")}</div></div>` : emptyState("▥", "No spending", "Expenses in this period will appear here.")}</section>
      <section class="card"><h2 style="margin-top:0">Budget performance</h2>${state.budgets.length ? state.budgets.slice(0, 5).map(budget => { const spent = budget.categoryId ? report.txs.filter(tx => tx.type === "expense" && tx.categoryId === budget.categoryId).reduce((sum, tx) => sum + tx.amount, 0) : report.expense; return budgetProgress(budget.categoryId ? categoryById(budget.categoryId)?.name || "Category" : "Overall budget", spent, budget.amount); }).join("") : `<p class="muted">Set budgets to compare planned and actual spending.</p>`}</section>
    </div>
    <div style="margin-top:18px">${sectionHeader("Transactions in this report")}<section class="card" style="padding:5px 8px">${recentTable([...report.txs].sort((a, b) => b.date.localeCompare(a.date)))}</section></div>`;
}

function renderSettings() {
  const theme = state.settings.theme;
  const lastBackup = state.settings.lastBackup ? `Last backup ${dateLabel(state.settings.lastBackup)}` : "No backup yet";
  const portable = location.protocol === "file:";
  return `
    <div class="grid cols-2">
      <div class="stack">
        ${sectionHeader("Preferences")}
        <section class="card settings-list">
          ${settingRow("◐", "Appearance", `<select id="themeSetting" class="filter-select"><option value="system" ${theme === "system" ? "selected" : ""}>Follow device</option><option value="light" ${theme === "light" ? "selected" : ""}>Light</option><option value="dark" ${theme === "dark" ? "selected" : ""}>Dark</option></select>`, "Choose light, dark or your device setting")}
          ${settingRow("€", "Currency", `<select id="currencySetting" class="filter-select">${Object.keys(symbols).map(code => `<option ${state.settings.currency === code ? "selected" : ""}>${code}</option>`).join("")}</select>`, "Display currency; amounts are not converted")}
          ${settingRow("◫", "Payday", `<select id="paydaySetting" class="filter-select"><option value="0">Plan to month end</option>${Array.from({ length: 31 }, (_, i) => `<option value="${i + 1}" ${state.settings.payday === i + 1 ? "selected" : ""}>Day ${i + 1}</option>`).join("")}</select>`, "Used by safe-to-spend")}
          ${settingRow("◇", "Planned monthly savings", `<button class="secondary-button" id="plannedSavingsSetting">${money(state.settings.plannedSavings)}</button>`, "Reserved from your safe allowance")}
        </section>
        ${sectionHeader("Backup & export")}
        <section class="card settings-list">
          ${settingRow("🔒", "Encrypted backup", `<button class="secondary-button" id="encryptedBackup">Create</button>`, `Password-protected copy of everything · ${lastBackup}`)}
          ${settingRow("↓", "Plain JSON export", `<button class="secondary-button" id="plainBackup">Export</button>`, "Readable and not encrypted")}
          ${settingRow("↑", "Restore backup", `<button class="secondary-button" id="restoreBackup">Choose file</button>`, "Replaces the data on this device")}
        </section>
      </div>
      <div class="stack">
        ${sectionHeader("This device")}
        <section class="card settings-list">
          ${portable ? "" : installRow()}
          ${settingRow("⌁", "Private by design", "", "No account, ads or analytics. Your data never leaves this device unless you export it.")}
          ${settingRow("↺", "Explore sample data", `<button class="secondary-button" id="exploreSample">Open</button>`, "Try MyMoney with example finances. Your own data is not changed.")}
        </section>
        ${demoMode ? "" : `${sectionHeader("Danger zone")}
        <section class="card settings-list">
          ${settingRow("×", "Delete all data", `<button class="secondary-button danger" id="deleteAll">Delete</button>`, "Permanently erase MyMoney data on this device")}
        </section>`}
        <div class="assumption"><strong>Keep a backup:</strong> your data is stored only in this browser on this device. Clearing browser data or losing the device erases it, so save encrypted backups somewhere safe, such as cloud storage or a USB stick.</div>
        <p class="muted" style="font-size:10px">MyMoney 1.1 · ${portable ? "Windows portable edition" : "Web app"}</p>
      </div>
    </div>`;
}
function installRow() {
  if (matchMedia("(display-mode: standalone)").matches) return settingRow("✓", "Installed", "", "MyMoney is running as an app on this device");
  return installPrompt
    ? settingRow("⤓", "Install on this device", `<button class="secondary-button" id="installSetting">Install</button>`, "Adds MyMoney to your home screen or Start menu and works offline")
    : settingRow("⤓", "Install on this device", "", "Open your browser menu and choose Install app or Add to Home screen");
}
function settingRow(icon, title, control, subtitle) {
  return `<div class="setting-row"><span class="setting-icon">${icon}</span><div class="setting-copy"><strong>${title}</strong><small>${subtitle}</small></div>${control}</div>`;
}

function field(id, label, value = "", type = "text", attrs = "", full = false) {
  return `<div class="field ${full ? "full" : ""}"><label for="${id}">${label}</label><input id="${id}" name="${id}" type="${type}" value="${escapeHtml(value)}" ${attrs}></div>`;
}
function selectField(id, label, options, selected, full = false) {
  return `<div class="field ${full ? "full" : ""}"><label for="${id}">${label}</label><select id="${id}" name="${id}">${options.map(option => { const value = typeof option === "string" ? option : option.value; const text = typeof option === "string" ? option : option.label; return `<option value="${escapeHtml(value)}" ${String(selected) === String(value) ? "selected" : ""}>${escapeHtml(text)}</option>`; }).join("")}</select></div>`;
}
function modal(title, body, saveLabel = "Save", onSave = null, dangerAction = "") {
  const root = $("#modal");
  root.innerHTML = `<div class="modal-header"><div><p class="eyebrow">MYMONEY</p><h2>${title}</h2></div><button class="close-button" data-close-modal type="button">×</button></div>${body}<p id="modalError" class="error-message"></p><div class="modal-actions">${dangerAction}<button class="secondary-button" data-close-modal type="button">Cancel</button>${onSave ? `<button class="primary-button" id="modalSave" type="button">${saveLabel}</button>` : ""}</div>`;
  $("#modalBackdrop").hidden = false;
  $$("[data-close-modal]", root).forEach(button => button.onclick = closeModal);
  if (onSave) {
    const saveButton = $("#modalSave");
    saveButton.onclick = async () => {
      saveButton.disabled = true;
      try { await onSave(); } finally { saveButton.disabled = false; }
    };
  }
}
function closeModal() { $("#modalBackdrop").hidden = true; $("#modal").innerHTML = ""; }
function modalError(message) { $("#modalError").textContent = message; }
function optionsForAccounts(selected, includeNone = false) {
  return (includeNone ? [{ value: "", label: "No account movement" }] : []).concat(state.accounts.map(account => ({ value: account.id, label: `${account.name} (${money(accountBalance(account.id))})` })));
}

function setupModal() {
  modal("Set up your money", `<p class="muted">Start with your main account. You can add more accounts later.</p><div class="form-grid">${field("setupName", "Account name", "Everyday account")}${field("setupBalance", "Current balance", "0.00")}${selectField("setupCurrency", "Currency", Object.keys(symbols), "EUR")}${selectField("setupPayday", "Payday", [{ value: 0, label: "Plan to month end" }, ...Array.from({ length: 31 }, (_, i) => ({ value: i + 1, label: `Day ${i + 1}` }))], 0)}</div>`, "Start using MyMoney", () => {
    const balance = parseCents($("#setupBalance").value);
    if (!$("#setupName").value.trim() || balance == null) return modalError("Enter an account name and a valid balance.");
    state = emptyData();
    state.settings.currency = $("#setupCurrency").value;
    state.settings.payday = Number($("#setupPayday").value);
    state.accounts.push({ id: uid(), name: $("#setupName").value.trim(), type: "current", opening: balance, includeSpendable: true });
    if (!save()) return; $("#welcome")?.remove(); closeModal(); render();
  });
}

function transactionModal(existing = null, presetType = "expense") {
  if (!state.accounts.length) return accountModal(null, true);
  const tx = existing || { type: presetType, amount: 0, date: todayIso(), categoryId: "", accountId: state.accounts[0].id, toAccountId: state.accounts[1]?.id || state.accounts[0].id, description: "", method: "Card", notes: "" };
  const categories = state.categories.filter(category => category.kind === (tx.type === "income" ? "income" : "expense"));
  const receiptSrc = typeof tx.receipt === "string" && tx.receipt.startsWith("data:image/") ? tx.receipt : null;
  modal(existing ? "Edit transaction" : "New transaction", `<div class="form-grid">
    ${selectField("txEditType", "Type", [{ value: "expense", label: "Expense" }, { value: "income", label: "Income" }, { value: "transfer", label: "Transfer" }], tx.type)}
    ${field("txEditAmount", "Amount", amountInput(tx.amount), "text")}
    ${field("txEditDate", "Date", tx.date, "date")}
    ${selectField("txEditCategory", "Category", [{ value: "", label: "Uncategorised" }, ...categories.map(category => ({ value: category.id, label: `${category.icon} ${category.name}` }))], tx.categoryId || "")}
    ${selectField("txEditAccount", "From account", optionsForAccounts(tx.accountId), tx.accountId)}
    ${selectField("txEditTo", "To account", optionsForAccounts(tx.toAccountId), tx.toAccountId)}
    ${selectField("txEditMethod", "Payment method", ["Card", "Cash", "Bank transfer", "Direct debit", "Mobile payment", "Other"], tx.method)}
    ${field("txEditDescription", "Description", tx.description, "text", "", true)}
    <div class="field full"><label for="txEditNotes">Notes</label><textarea id="txEditNotes">${escapeHtml(tx.notes)}</textarea></div>
    ${receiptSrc ? `<div class="field full"><label>Receipt</label><img class="receipt-preview" src="${escapeHtml(receiptSrc)}" alt="Receipt photo"><label><input id="txRemoveReceipt" type="checkbox"> Remove this receipt</label></div>` : ""}
    <div class="field full"><label for="txReceipt">${receiptSrc ? "Replace receipt photo" : "Receipt photo (optional)"}</label><input id="txReceipt" type="file" accept="image/*"><small>Photos are resized and stored only on this device.</small></div>
  </div>`, existing ? "Save changes" : "Add transaction", async () => {
    const amount = parseCents($("#txEditAmount").value), type = $("#txEditType").value, from = $("#txEditAccount").value, to = $("#txEditTo").value;
    if (amount == null || amount <= 0) return modalError("Enter an amount greater than zero.");
    if (!/^\d{4}-\d{2}-\d{2}$/.test($("#txEditDate").value)) return modalError("Choose a date.");
    if (!from) return modalError("Choose an account.");
    if (type === "transfer" && (!to || to === from)) return modalError("Choose two different accounts for a transfer.");
    let receipt = $("#txRemoveReceipt")?.checked ? null : receiptSrc;
    const file = $("#txReceipt").files[0];
    if (file) {
      try { receipt = await compressImage(file); } catch { return modalError("This photo could not be read. Choose a JPG or PNG image."); }
    }
    const updated = { id: existing?.id || uid(), type, amount, date: $("#txEditDate").value, categoryId: type === "transfer" ? null : $("#txEditCategory").value || null, accountId: from, toAccountId: type === "transfer" ? to : null, description: $("#txEditDescription").value.trim(), method: $("#txEditMethod").value, notes: $("#txEditNotes").value.trim(), receipt };
    if (existing) state.transactions[state.transactions.findIndex(item => item.id === existing.id)] = updated; else state.transactions.push(updated);
    if (!save()) return; closeModal(); render(); showToast(existing ? "Transaction updated" : "Transaction added");
  }, existing ? `<button class="secondary-button danger" id="deleteTransaction" type="button">Delete</button>` : "");
  const syncTypeFields = () => {
    const type = $("#txEditType").value;
    $("#txEditCategory").closest(".field").hidden = type === "transfer";
    $("#txEditTo").closest(".field").hidden = type !== "transfer";
    $("label[for='txEditAccount']").textContent = type === "income" ? "Paid into" : type === "transfer" ? "From account" : "Paid from";
  };
  $("#txEditType").onchange = event => {
    const kind = event.target.value === "income" ? "income" : "expense";
    $("#txEditCategory").innerHTML = `<option value="">Uncategorised</option>${state.categories.filter(category => category.kind === kind).map(category => `<option value="${category.id}">${category.icon} ${escapeHtml(category.name)}</option>`).join("")}`;
    syncTypeFields();
  };
  syncTypeFields();
  if (existing) $("#deleteTransaction").onclick = () => { if (confirm("Delete this transaction permanently?")) { state.transactions = state.transactions.filter(item => item.id !== existing.id); if (!save()) return; closeModal(); render(); showToast("Transaction deleted"); } };
}

function accountModal(existing = null, returnToTransaction = false) {
  const account = existing || { name: "", type: "current", opening: 0, includeSpendable: true };
  modal(existing ? "Edit account" : "New account", `<div class="form-grid">${field("accountName", "Account name", account.name)}${selectField("accountType", "Account type", [{ value: "current", label: "Current account" }, { value: "savings", label: "Savings account" }, { value: "cash", label: "Cash wallet" }, { value: "credit", label: "Credit card" }, { value: "other", label: "Other" }], account.type)}${field("accountBalance", existing ? "Opening balance" : "Current balance", amountInput(Math.abs(account.opening)), "text")}<div class="field"><label><input id="accountNegative" type="checkbox" ${account.opening < 0 ? "checked" : ""}> Balance is negative / owed</label></div><div class="field full"><label><input id="accountSpendable" type="checkbox" ${account.includeSpendable ? "checked" : ""}> Include in safe-to-spend</label></div></div>`, "Save account", () => {
    const amount = parseCents($("#accountBalance").value);
    if (!$("#accountName").value.trim() || amount == null) return modalError("Enter a name and valid balance.");
    const updated = { id: existing?.id || uid(), name: $("#accountName").value.trim(), type: $("#accountType").value, opening: $("#accountNegative").checked ? -amount : amount, includeSpendable: $("#accountSpendable").checked };
    if (existing) state.accounts[state.accounts.findIndex(item => item.id === existing.id)] = updated; else state.accounts.push(updated);
    if (!save()) return; closeModal(); render(); showToast("Account saved");
    if (returnToTransaction) transactionModal();
  }, existing ? `<button class="secondary-button danger" id="deleteAccount" type="button">Delete</button>` : "");
  if (existing) $("#deleteAccount").onclick = () => {
    const used = state.transactions.some(tx => tx.accountId === existing.id || tx.toAccountId === existing.id) || state.bills.some(bill => bill.accountId === existing.id) || state.debts.some(debt => debt.accountId === existing.id);
    if (used) return modalError("This account is referenced by transactions, bills or debts and cannot be deleted.");
    if (confirm("Delete this unused account?")) { state.accounts = state.accounts.filter(item => item.id !== existing.id); if (!save()) return; closeModal(); render(); }
  };
}

function transferModal() {
  if (state.accounts.length < 2) return showToast("Add at least two accounts first");
  modal("Transfer money", `<div class="form-grid">${field("transferAmount", "Amount", "")}${selectField("transferFrom", "From", optionsForAccounts(), state.accounts[0].id)}${selectField("transferTo", "To", optionsForAccounts(), state.accounts[1].id)}${field("transferDate", "Date", todayIso(), "date")}${field("transferDescription", "Description", "Transfer", "text", "", true)}</div><p class="muted">Transfers update account balances but never count as income or spending.</p>`, "Record transfer", () => {
    const amount = parseCents($("#transferAmount").value), from = $("#transferFrom").value, to = $("#transferTo").value;
    if (!amount || from === to) return modalError("Enter an amount and choose two different accounts.");
    state.transactions.push({ id: uid(), type: "transfer", amount, date: $("#transferDate").value, accountId: from, toAccountId: to, categoryId: null, description: $("#transferDescription").value.trim(), method: "Bank transfer", notes: "" });
    if (!save()) return; closeModal(); render(); showToast("Transfer recorded");
  });
}

function budgetModal(existing = null, categoryId = null) {
  const budget = existing || { categoryId, amount: 0 };
  const categories = state.categories.filter(category => category.kind === "expense" && !state.budgets.some(item => item.categoryId === category.id && item.id !== existing?.id));
  modal(existing ? "Edit budget" : categoryId === null ? "Overall budget" : "Category budget", `<div class="form-grid">${categoryId === null || existing?.categoryId === null ? "" : selectField("budgetCategory", "Category", categories.map(category => ({ value: category.id, label: `${category.icon} ${category.name}` })), budget.categoryId)}${field("budgetAmount", "Monthly amount", amountInput(budget.amount))}</div>`, "Save budget", () => {
    const amount = parseCents($("#budgetAmount").value);
    if (!amount) return modalError("Enter a budget greater than zero.");
    const updated = { id: existing?.id || uid(), categoryId: existing?.categoryId ?? (categoryId === null ? null : $("#budgetCategory").value), amount };
    if (existing) state.budgets[state.budgets.findIndex(item => item.id === existing.id)] = updated; else state.budgets.push(updated);
    if (!save()) return; closeModal(); render();
  }, existing ? `<button class="secondary-button danger" id="deleteBudget" type="button">Remove budget</button>` : "");
  if (existing) $("#deleteBudget").onclick = () => { state.budgets = state.budgets.filter(item => item.id !== existing.id); if (!save()) return; closeModal(); render(); };
}

function planModal() {
  modal("Payday & planned savings", `<div class="form-grid">${selectField("planPayday", "Payday", [{ value: 0, label: "Plan to month end" }, ...Array.from({ length: 31 }, (_, i) => ({ value: i + 1, label: `Day ${i + 1}` }))], state.settings.payday)}${field("plannedSavings", "Planned savings per month", amountInput(state.settings.plannedSavings))}</div><p class="muted">Planned savings are protected in your safe-to-spend calculation until you transfer them to a savings account.</p>`, "Save plan", () => {
    const savings = parseCents($("#plannedSavings").value);
    if (savings == null) return modalError("Enter a valid savings amount.");
    state.settings.payday = Number($("#planPayday").value); state.settings.plannedSavings = savings; if (!save()) return; closeModal(); render();
  });
}

function goalModal(existing = null) {
  const goal = existing || { name: "", target: 0, initial: 0, targetDate: "", icon: "◇" };
  modal(existing ? "Goal details" : "New savings goal", `<div class="form-grid">${field("goalName", "Goal name", goal.name)}${field("goalIcon", "Icon or emoji", goal.icon)}${field("goalTarget", "Target amount", amountInput(goal.target))}${field("goalInitial", "Already saved", amountInput(goal.initial))}${field("goalDate", "Target date (optional)", goal.targetDate || "", "date", "", true)}</div>${existing ? `<div class="grid cols-2" style="margin-top:15px"><button class="secondary-button" id="contributeGoal">＋ Add contribution</button><button class="secondary-button" id="withdrawGoal">− Withdraw</button></div>` : ""}`, existing ? "Save changes" : "Create goal", () => {
    const target = parseCents($("#goalTarget").value), initial = parseCents($("#goalInitial").value || "0");
    if (!$("#goalName").value.trim() || !target || initial == null) return modalError("Enter a name and valid target.");
    const updated = { ...goal, id: existing?.id || uid(), name: $("#goalName").value.trim(), icon: $("#goalIcon").value.trim() || "◇", target, initial, targetDate: $("#goalDate").value || null, archived: false };
    if (existing) state.goals[state.goals.findIndex(item => item.id === existing.id)] = updated; else state.goals.push(updated);
    if (!save()) return; closeModal(); render();
  }, existing ? `<button class="secondary-button danger" id="deleteGoal" type="button">Delete</button>` : "");
  if (existing) {
    $("#contributeGoal").onclick = () => contributionModal(existing, false);
    $("#withdrawGoal").onclick = () => contributionModal(existing, true);
    $("#deleteGoal").onclick = () => { if (confirm("Delete this goal and its contribution history?")) { state.goals = state.goals.filter(item => item.id !== existing.id); state.goalEntries = state.goalEntries.filter(item => item.goalId !== existing.id); if (!save()) return; closeModal(); render(); } };
  }
}
function contributionModal(goal, withdraw) {
  modal(withdraw ? `Withdraw from ${escapeHtml(goal.name)}` : `Add to ${escapeHtml(goal.name)}`, `<div class="form-grid">${field("contributionAmount", "Amount", "")}${field("contributionDate", "Date", todayIso(), "date")}${field("contributionNote", "Note", "", "text", "", true)}</div><p class="muted">This allocates money to the goal without counting it as income or spending.</p>`, "Save", () => {
    const amount = parseCents($("#contributionAmount").value);
    if (!amount) return modalError("Enter an amount greater than zero.");
    if (withdraw && amount > goalSaved(goal)) return modalError("You cannot withdraw more than the saved amount.");
    state.goalEntries.push({ id: uid(), goalId: goal.id, amount: withdraw ? -amount : amount, date: $("#contributionDate").value, note: $("#contributionNote").value.trim() });
    if (!save()) return; closeModal(); render(); showToast(withdraw ? "Withdrawal recorded" : "Contribution added");
  });
}

function billModal(existing = null) {
  const bill = existing || { name: "", amount: 0, frequency: "Monthly", dueDate: todayIso(), categoryId: "", accountId: state.accounts[0]?.id || "", subscription: false, active: true };
  modal(existing ? "Edit bill" : "New bill", `<div class="form-grid">${field("billName", "Name", bill.name)}${field("billAmount", "Amount", amountInput(bill.amount))}${selectField("billFrequency", "Frequency", ["Weekly", "Every 2 weeks", "Monthly", "Quarterly", "Yearly"], bill.frequency)}${field("billDate", "Next due date", bill.dueDate, "date")}${selectField("billCategory", "Category", [{ value: "", label: "Uncategorised" }, ...state.categories.filter(category => category.kind === "expense").map(category => ({ value: category.id, label: `${category.icon} ${category.name}` }))], bill.categoryId || "")}${selectField("billAccount", "Usual account", optionsForAccounts(bill.accountId, true), bill.accountId || "")}<div class="field"><label><input id="billSubscription" type="checkbox" ${bill.subscription ? "checked" : ""}> Subscription</label></div><div class="field"><label><input id="billActive" type="checkbox" ${bill.active ? "checked" : ""}> Active</label></div></div>`, "Save bill", () => {
    const amount = parseCents($("#billAmount").value);
    if (!$("#billName").value.trim() || !amount) return modalError("Enter a name and amount.");
    const updated = { id: existing?.id || uid(), name: $("#billName").value.trim(), amount, frequency: $("#billFrequency").value, dueDate: $("#billDate").value, categoryId: $("#billCategory").value || null, accountId: $("#billAccount").value || null, subscription: $("#billSubscription").checked, active: $("#billActive").checked };
    if (existing) state.bills[state.bills.findIndex(item => item.id === existing.id)] = updated; else state.bills.push(updated);
    if (!save()) return; closeModal(); render();
  }, existing ? `<button class="secondary-button danger" id="deleteBill" type="button">Delete</button>` : "");
  if (existing) $("#deleteBill").onclick = () => { if (confirm("Delete this bill? Existing payment transactions will stay.")) { state.bills = state.bills.filter(item => item.id !== existing.id); if (!save()) return; closeModal(); render(); } };
}
function payBillModal(bill) {
  if (!state.accounts.length) return showToast("Add an account first");
  modal(`Pay ${escapeHtml(bill.name)}`, `<div class="form-grid">${field("billPayAmount", "Amount paid", amountInput(bill.amount))}${field("billPayDate", "Payment date", todayIso(), "date")}${selectField("billPayAccount", "Account", optionsForAccounts(), bill.accountId || state.accounts[0].id)}</div><p class="muted">This records an expense and advances the due date. MyMoney never deducts bills automatically.</p>`, "Mark paid", () => {
    const amount = parseCents($("#billPayAmount").value);
    if (!amount) return modalError("Enter the amount paid.");
    state.transactions.push({ id: uid(), type: "expense", amount, date: $("#billPayDate").value, categoryId: bill.categoryId, accountId: $("#billPayAccount").value, description: bill.name, method: "Direct debit", notes: "", billId: bill.id });
    bill.dueDate = advanceDate(bill.dueDate, bill.frequency);
    if (!save()) return; closeModal(); render(); showToast("Bill marked as paid");
  });
}
function advanceDate(value, frequency) {
  const date = new Date(`${value}T12:00:00`);
  if (frequency === "Weekly") date.setDate(date.getDate() + 7);
  else if (frequency === "Every 2 weeks") date.setDate(date.getDate() + 14);
  else {
    const months = frequency === "Quarterly" ? 3 : frequency === "Yearly" ? 12 : 1;
    const day = date.getDate();
    date.setDate(1);
    date.setMonth(date.getMonth() + months);
    date.setDate(Math.min(day, new Date(date.getFullYear(), date.getMonth() + 1, 0).getDate()));
  }
  return isoDate(date);
}

function debtModal(existing = null) {
  const debt = existing || { name: "", kind: "loan", counterparty: "", principal: 0, rateBps: 0, startDate: todayIso(), monthlyPayment: 0, accountId: "", closed: false };
  modal(existing ? "Edit debt" : "Add debt or loan", `<div class="form-grid">${selectField("debtKind", "Type", [{ value: "loan", label: "Loan (I owe)" }, { value: "borrowed", label: "Borrowed from someone" }, { value: "lent", label: "Lent to someone" }], debt.kind)}${field("debtName", "Name", debt.name)}${field("debtCounterparty", "Lender / borrower", debt.counterparty)}${field("debtPrincipal", "Original principal", amountInput(debt.principal))}${field("debtRate", "Annual interest (%)", (debt.rateBps / 100).toFixed(2), "number", 'step="0.01" min="0" max="100"')}${field("debtStart", "Start date", debt.startDate, "date")}${field("debtMonthly", "Monthly repayment", amountInput(debt.monthlyPayment))}${selectField("debtAccount", "Linked account", optionsForAccounts(debt.accountId, true), debt.accountId || "")}</div>`, "Save debt", () => {
    const principal = parseCents($("#debtPrincipal").value), monthlyPayment = parseCents($("#debtMonthly").value || "0"), rate = Number($("#debtRate").value);
    if (!$("#debtName").value.trim() || !principal || monthlyPayment == null || !Number.isFinite(rate)) return modalError("Enter a name and valid amounts.");
    const updated = { id: existing?.id || uid(), name: $("#debtName").value.trim(), kind: $("#debtKind").value, counterparty: $("#debtCounterparty").value.trim(), principal, rateBps: Math.round(rate * 100), startDate: $("#debtStart").value, monthlyPayment, accountId: $("#debtAccount").value || null, closed: debt.closed };
    if (existing) state.debts[state.debts.findIndex(item => item.id === existing.id)] = updated; else state.debts.push(updated);
    if (!save()) return; closeModal(); render();
  }, existing ? `<button class="secondary-button danger" id="deleteDebt" type="button">Delete</button>` : "");
  if (existing) $("#deleteDebt").onclick = () => { if (confirm("Delete this debt and its repayment history?")) { state.debts = state.debts.filter(item => item.id !== existing.id); state.debtEntries = state.debtEntries.filter(item => item.debtId !== existing.id); if (!save()) return; closeModal(); render(); } };
}
function debtPaymentModal(debt) {
  modal(debt.kind === "lent" ? "Record payment received" : "Record repayment", `<div class="form-grid">${field("debtPayTotal", "Total payment", amountInput(Math.min(debt.monthlyPayment || debtOutstanding(debt), debtOutstanding(debt))))}${field("debtPayInterest", "Interest part", "0.00")}${field("debtPayDate", "Date", todayIso(), "date")}${selectField("debtPayAccount", "Account movement", optionsForAccounts(debt.accountId, true), debt.accountId || "")}${field("debtPayNote", "Note", "", "text", "", true)}</div><p class="muted">Principal reduces the debt without counting as spending. Interest is a real expense, or income for money you lent.</p>`, "Save repayment", () => {
    const total = parseCents($("#debtPayTotal").value), interest = parseCents($("#debtPayInterest").value || "0"), outstanding = debtOutstanding(debt);
    if (!total || interest == null || interest > total || total - interest > outstanding) return modalError("Check the payment and interest amounts.");
    const principal = total - interest, accountId = $("#debtPayAccount").value || null, entryId = uid();
    state.debtEntries.push({ id: entryId, debtId: debt.id, kind: "repayment", date: $("#debtPayDate").value, principal, interest, accountId, note: $("#debtPayNote").value.trim() });
    if (accountId && principal > 0) state.transactions.push({ id: uid(), type: debt.kind === "lent" ? "debt_in" : "debt_out", amount: principal, date: $("#debtPayDate").value, accountId, categoryId: null, description: `Repayment: ${debt.name}`, method: "Bank transfer", notes: "", debtEntryId: entryId });
    if (accountId && interest > 0) state.transactions.push({ id: uid(), type: debt.kind === "lent" ? "income" : "expense", amount: interest, date: $("#debtPayDate").value, accountId, categoryId: state.categories.find(cat => cat.name === "Interest" && cat.kind === (debt.kind === "lent" ? "income" : "expense"))?.id || null, description: `Interest: ${debt.name}`, method: "Bank transfer", notes: "", debtEntryId: entryId });
    if (!save()) return; closeModal(); render(); showToast("Repayment recorded");
  });
}

function bindPage() {
  $$("[data-route]").forEach(button => button.onclick = () => navigate(button.dataset.route));
  $$("[data-edit-tx]").forEach(row => row.onclick = () => transactionModal(state.transactions.find(tx => tx.id === row.dataset.editTx)));
  $$("[data-edit-account]").forEach(card => card.onclick = () => accountModal(state.accounts.find(account => account.id === card.dataset.editAccount)));
  $$("[data-edit-budget]").forEach(card => card.onclick = () => budgetModal(state.budgets.find(budget => budget.id === card.dataset.editBudget)));
  $$("[data-goal]").forEach(card => card.onclick = () => goalModal(state.goals.find(goal => goal.id === card.dataset.goal)));
  $$("[data-edit-bill]").forEach(button => button.onclick = () => billModal(state.bills.find(bill => bill.id === button.dataset.editBill)));
  $$("[data-pay-bill]").forEach(button => button.onclick = () => payBillModal(state.bills.find(bill => bill.id === button.dataset.payBill)));
  $$("[data-edit-debt]").forEach(button => button.onclick = () => debtModal(state.debts.find(debt => debt.id === button.dataset.editDebt)));
  $$("[data-pay-debt]").forEach(button => button.onclick = () => debtPaymentModal(state.debts.find(debt => debt.id === button.dataset.payDebt)));
  $$("[data-report-period]").forEach(button => button.onclick = () => { reportPeriod = button.dataset.reportPeriod; reportOffset = 0; render(); });
  const click = (id, handler) => { const element = $(`#${id}`); if (element) element.onclick = handler; };
  click("openReports", () => navigate("reports")); click("seeAllTransactions", () => navigate("transactions")); click("openBills", () => navigate("bills")); click("openGoals", () => navigate("goals"));
  click("installNow", promptInstall); click("backupNow", encryptedBackupModal); click("exitDemo", exitDemo);
  click("newAccount", () => accountModal()); click("newAccountEmpty", () => accountModal()); click("newTransfer", transferModal);
  click("editOverallBudget", () => budgetModal(state.budgets.find(budget => budget.categoryId === null), null)); click("setOverallEmpty", () => budgetModal(null, null));
  click("addCategoryBudget", () => budgetModal(null, "choose")); click("editPlan", planModal);
  click("newGoal", () => goalModal()); click("newGoalEmpty", () => goalModal());
  click("newBill", () => billModal()); click("newBillEmpty", () => billModal());
  click("newDebt", () => debtModal()); click("newDebtEmpty", () => debtModal());
  click("exportTxCsv", () => exportTransactionsCsv(state.transactions));
  click("calculatePlan", () => {
    const income = parseCents($("#planIncome").value) || 0, expense = parseCents($("#planExpense").value) || 0, start = parseCents($("#planStart").value) || 0, target = parseCents($("#planTarget").value) || 0;
    $("#forecastOutput").innerHTML = forecastOutput(income, expense, start, Math.round(Number($("#planRate").value || 0) * 100), clamp(Number($("#planReduce").value || 0), 0, 80), target);
  });
  click("reportPrev", () => { reportOffset--; render(); }); click("reportNext", () => { if (reportOffset < 0) reportOffset++; render(); });
  click("reportCsv", () => exportTransactionsCsv(reportData().txs, `mymoney-report-${reportData().start}-${reportData().end}.csv`)); click("reportPrint", () => window.print());
  const txSearch = $("#txSearch"); if (txSearch) txSearch.oninput = event => { transactionQuery = event.target.value; render(); $("#txSearch")?.focus(); };
  const txType = $("#txType"); if (txType) txType.onchange = event => { transactionType = event.target.value; render(); };
  bindSettings();
}

function bindSettings() {
  const theme = $("#themeSetting"); if (theme) theme.onchange = event => { state.settings.theme = event.target.value; if (!save()) return; render(); };
  const currency = $("#currencySetting"); if (currency) currency.onchange = event => { state.settings.currency = event.target.value; if (!save()) return; render(); };
  const payday = $("#paydaySetting"); if (payday) payday.onchange = event => { state.settings.payday = Number(event.target.value); if (!save()) return; render(); };
  const click = (id, handler) => { const element = $(`#${id}`); if (element) element.onclick = handler; };
  click("plannedSavingsSetting", planModal);
  click("plainBackup", () => { downloadJson(state, `mymoney-export-${todayIso()}.json`); markBackedUp(); });
  click("encryptedBackup", encryptedBackupModal);
  click("restoreBackup", () => $("#importFile").click());
  click("installSetting", promptInstall);
  click("exploreSample", () => { enterDemo(); showToast("Showing sample data — nothing is saved"); });
  click("deleteAll", () => {
    const confirmation = prompt('Type DELETE to permanently erase all MyMoney data on this device.');
    if (confirmation === "DELETE") { localStorage.removeItem(STORAGE_KEY); state = null; location.reload(); }
  });
}
function markBackedUp() {
  state.settings.lastBackup = todayIso();
  if (save()) render();
}
async function promptInstall() {
  if (!installPrompt) return;
  const pending = installPrompt;
  installPrompt = null;
  await pending.prompt();
  if (state) render();
}

async function encryptedBackupModal() {
  modal("Create encrypted backup", `<p class="muted">Use at least 8 characters. There is no password recovery.</p><div class="form-grid">${field("backupPassword", "Password", "", "password")}${field("backupRepeat", "Repeat password", "", "password")}</div>`, "Encrypt & save", async () => {
    const password = $("#backupPassword").value;
    if (password.length < 8 || password !== $("#backupRepeat").value) return modalError("Use at least 8 characters and make both passwords match.");
    try {
      const envelope = await encryptBackup(state, password);
      downloadJson(envelope, `mymoney-backup-${todayIso()}.mymoney`);
      closeModal(); markBackedUp(); showToast("Encrypted backup saved");
    } catch (error) { modalError(`Backup failed: ${error.message}`); }
  });
}
async function encryptBackup(data, password) {
  const salt = crypto.getRandomValues(new Uint8Array(16)), iv = crypto.getRandomValues(new Uint8Array(12));
  const material = await crypto.subtle.importKey("raw", new TextEncoder().encode(password), "PBKDF2", false, ["deriveKey"]);
  const key = await crypto.subtle.deriveKey({ name: "PBKDF2", hash: "SHA-256", salt, iterations: 150000 }, material, { name: "AES-GCM", length: 256 }, false, ["encrypt"]);
  const encrypted = await crypto.subtle.encrypt({ name: "AES-GCM", iv }, key, new TextEncoder().encode(JSON.stringify(data)));
  return { format: "mymoney-desktop-encrypted", version: 1, iterations: 150000, salt: bytesToBase64(salt), iv: bytesToBase64(iv), data: bytesToBase64(new Uint8Array(encrypted)) };
}
async function decryptBackup(envelope, password) {
  const salt = base64ToBytes(envelope.salt), iv = base64ToBytes(envelope.iv), encrypted = base64ToBytes(envelope.data);
  const material = await crypto.subtle.importKey("raw", new TextEncoder().encode(password), "PBKDF2", false, ["deriveKey"]);
  const key = await crypto.subtle.deriveKey({ name: "PBKDF2", hash: "SHA-256", salt, iterations: envelope.iterations }, material, { name: "AES-GCM", length: 256 }, false, ["decrypt"]);
  const plain = await crypto.subtle.decrypt({ name: "AES-GCM", iv }, key, encrypted);
  return JSON.parse(new TextDecoder().decode(plain));
}
const bytesToBase64 = bytes => { let binary = ""; bytes.forEach(byte => binary += String.fromCharCode(byte)); return btoa(binary); };
const base64ToBytes = text => Uint8Array.from(atob(text), char => char.charCodeAt(0));
async function compressImage(file, maxSide = 1280) {
  const bitmap = await createImageBitmap(file);
  const scale = Math.min(1, maxSide / Math.max(bitmap.width, bitmap.height));
  const canvas = document.createElement("canvas");
  canvas.width = Math.max(1, Math.round(bitmap.width * scale));
  canvas.height = Math.max(1, Math.round(bitmap.height * scale));
  const context = canvas.getContext("2d");
  context.fillStyle = "#fff";
  context.fillRect(0, 0, canvas.width, canvas.height);
  context.drawImage(bitmap, 0, 0, canvas.width, canvas.height);
  bitmap.close();
  const webp = canvas.toDataURL("image/webp", 0.7);
  return webp.startsWith("data:image/webp") ? webp : canvas.toDataURL("image/jpeg", 0.72);
}

function validateImported(data) {
  if (!data || data.version !== 1 || !data.settings || !Array.isArray(data.accounts) || !Array.isArray(data.transactions)) throw new Error("This is not a supported MyMoney backup.");
  for (const collection of ["accounts", "categories", "transactions", "budgets", "goals", "goalEntries", "bills", "debts", "debtEntries"]) {
    if (!Array.isArray(data[collection])) throw new Error(`The backup is missing ${collection}.`);
  }
  return data;
}
async function importBackup(file) {
  try {
    let imported = JSON.parse(await file.text());
    if (imported.format === "mymoney-desktop-encrypted") {
      const password = prompt("Enter the password for this encrypted backup:");
      if (!password) return;
      try { imported = await decryptBackup(imported, password); } catch { throw new Error("the password is wrong or the file is damaged."); }
    }
    validateImported(imported);
    if (!confirm(`Restore ${plural(imported.transactions.length, "transaction")} and ${plural(imported.accounts.length, "account")}? This replaces current data.`)) return;
    if (demoMode) { demoMode = false; history.replaceState(null, "", location.pathname); }
    state = imported;
    if (!save()) return;
    route = "dashboard"; render(); showToast("Backup restored");
  } catch (error) { alert(`Could not restore backup: ${error.message}`); }
}
function downloadJson(data, name) { downloadBlob(new Blob([JSON.stringify(data, null, 2)], { type: "application/json" }), name); }
function exportTransactionsCsv(transactions, name = `mymoney-transactions-${todayIso()}.csv`) {
  const csvCell = value => `"${String(value ?? "").replaceAll('"', '""')}"`;
  const rows = [["Date", "Type", `Amount (${state.settings.currency})`, "Category", "Account", "To account", "Description", "Notes"]];
  [...transactions].sort((a, b) => a.date.localeCompare(b.date)).forEach(tx => rows.push([tx.date, tx.type, `${Math.floor(tx.amount / 100)}.${String(tx.amount % 100).padStart(2, "0")}`, categoryById(tx.categoryId)?.name || "", accountById(tx.accountId)?.name || "", accountById(tx.toAccountId)?.name || "", tx.description || "", tx.notes || ""]));
  downloadBlob(new Blob(["\uFEFF" + rows.map(row => row.map(csvCell).join(",")).join("\r\n")], { type: "text/csv;charset=utf-8" }), name);
}
function downloadBlob(blob, name) {
  const link = document.createElement("a"); link.href = URL.createObjectURL(blob); link.download = name; link.click(); setTimeout(() => URL.revokeObjectURL(link.href), 60000);
}

$("#mainNav").onclick = event => { const button = event.target.closest("[data-route]"); if (button) navigate(button.dataset.route); };
$("#addButton").onclick = () => state ? transactionModal() : null;
$("#themeToggle").onclick = () => { if (!state) return; const dark = document.body.classList.contains("dark"); state.settings.theme = dark ? "light" : "dark"; if (!save()) return; render(); };
$("#menuToggle").onclick = () => document.body.classList.toggle("menu-open");
$("#menuScrim").onclick = () => document.body.classList.remove("menu-open");
$("#modalBackdrop").onclick = event => { if (event.target === $("#modalBackdrop")) closeModal(); };
$("#importFile").onchange = event => { const file = event.target.files[0]; if (file) importBackup(file); event.target.value = ""; };
window.addEventListener("keydown", event => { if (event.key === "Escape" && !$("#modalBackdrop").hidden) closeModal(); });
matchMedia("(prefers-color-scheme: dark)").addEventListener("change", () => { if (state?.settings?.theme === "system") render(); });
window.addEventListener("beforeinstallprompt", event => { event.preventDefault(); installPrompt = event; if (state) render(); });
window.addEventListener("appinstalled", () => { installPrompt = null; if (state) render(); showToast("MyMoney is installed"); });
if ("serviceWorker" in navigator && location.protocol !== "file:") navigator.serviceWorker.register("./sw.js").catch(() => {});

render();
if (launchParams.get("action") === "add") {
  launchParams.delete("action");
  const query = launchParams.toString();
  history.replaceState(null, "", query ? `${location.pathname}?${query}` : location.pathname);
  if (state) transactionModal();
}
