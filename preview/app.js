const transactions = [
  ["🛒", "Fresh Market", "Groceries · Today", "-€42.80", "negative"],
  ["☕", "Café Central", "Eating out · Today", "-€6.40", "negative"],
  ["💼", "Acme salary", "Income · 1 Oct", "+€3,250.00", "positive"],
  ["⚡", "Green Energy", "Utilities · 30 Sep", "-€86.20", "negative"],
];

const tabs = [
  { id: "home", label: "Home", icon: "⌂" },
  { id: "transactions", label: "Transactions", icon: "↕" },
  { id: "budgets", label: "Budgets", icon: "◔" },
  { id: "savings", label: "Savings", icon: "◇" },
  { id: "more", label: "More", icon: "•••" },
];

const moneyRows = (rows = transactions) => rows.map(([icon, title, subtitle, amount, tone]) => `
  <div class="transaction">
    <div class="tx-icon">${icon}</div>
    <div class="tx-copy"><strong>${title}</strong><small>${subtitle}</small></div>
    <span class="amount ${tone}">${amount}</span>
  </div>`).join("");

const bars = [
  ["May", 46, 39], ["Jun", 61, 49], ["Jul", 53, 56],
  ["Aug", 70, 45], ["Sep", 64, 52], ["Oct", 75, 34],
].map(([label, income, expense]) => `
  <div class="bar-pair">
    <i class="bar" style="height:${income}%"></i><i class="bar expense" style="height:${expense}%"></i>
    <span class="bar-label">${label}</span>
  </div>`).join("");

const header = (title) => `<header class="page-header"><h2>${title}</h2><button class="avatar" type="button">A</button></header>`;

const screens = {
  home: () => `
    ${header("Good morning")}
    <section class="hero">
      <small>Total money</small>
      <strong class="hero-balance">€12,840.65</strong>
      <div class="hero-stats">
        <div class="hero-stat"><small>Income</small><strong>€3,250</strong></div>
        <div class="hero-stat"><small>Spent</small><strong>€1,846</strong></div>
        <div class="hero-stat"><small>Saved</small><strong>€1,404</strong></div>
      </div>
    </section>
    <div class="section-head"><h3>Safe to spend</h3><button data-go="budgets">View plan</button></div>
    <section class="card safe-card">
      <div><small>Per day until 31 Oct</small><strong>€46.20</strong><small>€1,016.40 available after bills</small></div>
      <div class="daily-ring"></div>
    </section>
    <div class="section-head"><h3>Six-month overview</h3><button>October</button></div>
    <section class="card chart-card">
      <div class="bar-chart">${bars}</div>
      <div class="legend"><span><i class="dot"></i>Income</span><span><i class="dot expense"></i>Expenses</span></div>
    </section>
    <div class="section-head"><h3>Recent activity</h3><button data-go="transactions">See all</button></div>
    <section class="card">${moneyRows(transactions.slice(0, 3))}</section>`,

  transactions: () => `
    ${header("Transactions")}
    <div class="search">⌕&nbsp;&nbsp; Search transactions</div>
    <div class="chips"><button class="chip active">All</button><button class="chip">Expenses</button><button class="chip">Income</button><button class="chip">This month</button></div>
    <div class="summary-grid">
      <section class="card mini-card"><small>Money in</small><strong class="amount positive">€3,250</strong></section>
      <section class="card mini-card"><small>Money out</small><strong class="amount negative">€1,846</strong></section>
    </div>
    <p class="date-label">Today · 20 October</p>
    <section class="card">${moneyRows(transactions.slice(0, 2))}</section>
    <p class="date-label">Earlier this month</p>
    <section class="card">${moneyRows(transactions.slice(2))}</section>`,

  budgets: () => `
    ${header("Budgets")}
    <section class="card safe-card">
      <div><small>Safe today</small><strong>€46.20</strong><small>22 days left in your plan</small></div>
      <div class="daily-ring"></div>
    </section>
    <div class="section-head"><h3>October budget</h3><button>Edit plan</button></div>
    <section class="card">
      <div class="budget-row"><div class="row-title"><strong>Overall</strong><span>€1,846 of €2,500</span></div><div class="progress"><i style="width:74%"></i></div><p class="muted">€654 remaining</p></div>
      <div class="budget-row"><div class="row-title"><strong>🛒 Groceries</strong><span>€384 of €450</span></div><div class="progress"><i class="warning" style="width:85%"></i></div><p class="muted">€66 remaining</p></div>
      <div class="budget-row"><div class="row-title"><strong>☕ Eating out</strong><span>€192 of €160</span></div><div class="progress"><i class="danger" style="width:100%"></i></div><p class="warning-copy">€32 over budget</p></div>
      <div class="budget-row"><div class="row-title"><strong>🚗 Transport</strong><span>€96 of €220</span></div><div class="progress"><i style="width:44%"></i></div><p class="muted">€124 remaining</p></div>
    </section>
    <div class="section-head"><h3>Protected money</h3></div>
    <section class="card"><div class="row-title"><strong>Upcoming bills</strong><span>€286.40</span></div><div class="row-title" style="margin-top:12px"><strong>Planned savings</strong><span>€350.00</span></div></section>`,

  savings: () => `
    ${header("Savings goals")}
    <section class="hero">
      <small>Saved towards goals</small>
      <strong class="hero-balance">€7,760.00</strong>
      <div class="hero-stats">
        <div class="hero-stat"><small>Target</small><strong>€18,500</strong></div>
        <div class="hero-stat"><small>Progress</small><strong>42%</strong></div>
        <div class="hero-stat"><small>Monthly</small><strong>€540</strong></div>
      </div>
    </section>
    <div class="section-head"><h3>Your goals</h3><button>+ New goal</button></div>
    <section class="card">
      <div class="goal-row"><div class="goal-top"><div class="goal-icon">🛟</div><div><strong>Emergency fund</strong><small>€5,200 of €9,000</small></div><span class="goal-percent">58%</span></div><div class="progress"><i style="width:58%"></i></div><p class="muted">€315/month · on track for Oct 2027</p></div>
      <div class="goal-row"><div class="goal-top"><div class="goal-icon">🏖️</div><div><strong>Portugal</strong><small>€1,560 of €3,000</small></div><span class="goal-percent">52%</span></div><div class="progress"><i style="width:52%"></i></div><p class="muted">€180/month · on track for Jun 2027</p></div>
      <div class="goal-row"><div class="goal-top"><div class="goal-icon">💻</div><div><strong>New laptop</strong><small>€1,000 of €2,500</small></div><span class="goal-percent">40%</span></div><div class="progress"><i style="width:40%"></i></div><p class="muted">€125/month · target Dec 2027</p></div>
    </section>`,

  more: () => `
    ${header("More")}
    <div class="menu-group"><p class="menu-label">Money</p><section class="card">
      ${menu("🏦", "Accounts", "Balances, opening amounts and transfers")}
      ${menu("🧾", "Bills & subscriptions", "Upcoming payments, calendar and reminders")}
      ${menu("⚖️", "Debts & loans", "What you owe and what you're owed")}
    </section></div>
    <div class="menu-group"><p class="menu-label">Insights</p><section class="card">
      ${menu("↗", "Planning", "Forecasts and what-if calculators")}
      ${menu("▥", "Reports", "Weekly, monthly and yearly summaries")}
    </section></div>
    <div class="menu-group"><p class="menu-label">App</p><section class="card">
      ${menu("◉", "Categories", "Default and custom categories")}
      ${menu("⚙", "Settings", "Appearance, app lock, backup and data")}
    </section></div>`,
};

function menu(icon, title, subtitle) {
  return `<div class="menu-row"><span>${icon}</span><div><strong>${title}</strong><small>${subtitle}</small></div><span class="chevron">›</span></div>`;
}

let activeTab = "home";
const screen = document.querySelector("#screen");
const nav = document.querySelector("#bottomNav");
const sheet = document.querySelector("#sheetBackdrop");

function render() {
  screen.innerHTML = screens[activeTab]();
  screen.scrollTop = 0;
  nav.innerHTML = tabs.map(tab => `
    <button class="nav-button ${tab.id === activeTab ? "active" : ""}" data-tab="${tab.id}" type="button">
      <span class="nav-icon">${tab.icon}</span><span>${tab.label}</span>
    </button>`).join("");
  nav.querySelectorAll("[data-tab]").forEach(button => button.addEventListener("click", () => {
    activeTab = button.dataset.tab;
    render();
  }));
  screen.querySelectorAll("[data-go]").forEach(button => button.addEventListener("click", () => {
    activeTab = button.dataset.go;
    render();
  }));
}

document.querySelector("#quickAdd").addEventListener("click", () => { sheet.hidden = false; });
document.querySelector("#closeSheet").addEventListener("click", () => { sheet.hidden = true; });
sheet.addEventListener("click", event => { if (event.target === sheet) sheet.hidden = true; });

document.querySelectorAll(".segment-button").forEach(button => button.addEventListener("click", () => {
  document.querySelectorAll(".segment-button").forEach(item => item.classList.toggle("active", item === button));
  document.querySelector("#saveTransaction").textContent = `Save ${button.dataset.kind.toLowerCase()}`;
}));

document.querySelectorAll(".category-option").forEach(button => button.addEventListener("click", () => {
  document.querySelectorAll(".category-option").forEach(item => item.classList.toggle("selected", item === button));
}));

document.querySelector("#saveTransaction").addEventListener("click", () => {
  sheet.hidden = true;
  const toast = document.querySelector("#toast");
  toast.classList.add("show");
  setTimeout(() => toast.classList.remove("show"), 1600);
});

const setTheme = dark => {
  document.body.classList.toggle("dark", dark);
  document.querySelector("#darkTheme").classList.toggle("active", dark);
  document.querySelector("#lightTheme").classList.toggle("active", !dark);
};
document.querySelector("#lightTheme").addEventListener("click", () => setTheme(false));
document.querySelector("#darkTheme").addEventListener("click", () => setTheme(true));

render();
