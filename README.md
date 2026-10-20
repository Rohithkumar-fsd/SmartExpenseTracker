# ⚡ SmartSpend AI - Smart Expense Tracker & Telegram Bot

An enterprise-grade, intelligent expense tracker built with **Java (Spring Boot 3)**, **PostgreSQL / H2**, and the **TelegramBots API**. 

It eliminates the two biggest flaws of personal finance apps:
1. **Tedious Manual Forms:** Log expenses via natural text (e.g., `Coffee 4.50` or `Uber 25`) through Telegram or the Web UI.
2. **Reactive Alerts:** Instead of warning you *after* you're broke, it calculates your **spending speed (burn-rate velocity)** and warns you days before your budget runs out.

---

## 🌟 Unique Core Features

- 🏎️ **Speedometer (Burn-Rate Velocity Alerts):** Early-warning system that projects your monthly total and recommends a safe daily speed limit.
- ☀️ **Daily "Safe-to-Spend" Allowance:** Calculates how much you can guilt-free spend today: `(Remaining Budget) / (Days Left in Month)`.
- 🤔 **"Can I Afford This?" Pre-Purchase Simulator:** Test an expense before buying (e.g. `$180 Headset`). Gives a clear verdict (`SAFE`, `CAUTION`, `RISKY`) and compares your daily allowance before vs. after.
- 🤖 **Two-Way Telegram Bot Sync:** Chat with your bot directly on your phone; all transactions instantly appear on the Web Dashboard in real-time.
- 📊 **Glassmorphic Web Dashboard:** Cyber dark theme with live Chart.js donut charts, category progress bars, and alert streams.

---

## 🏛️ System Architecture

```
Telegram Chat (Phone / PC)          Web Browser (Dashboard)
          │                                   │
          ▼                                   ▼
[ SmartExpenseBot.java ]             [ ExpenseApiController.java ]
          │                                   │
          └───────────────┬───────────────────┘
                          │
                          ▼
            [ ExpenseParserService.java ]   (NLP text extraction)
            [ AlertEngineService.java ]    (Velocity & Anomaly math)
            [ BudgetService.java ]         (Safe-to-Spend & Simulator)
                          │
                          ▼
             Spring Data JPA Repositories
                          │
                          ▼
       PostgreSQL (Cloud) or H2 (Local File)
```

---

## 🗄️ Database Schema

The application uses **Spring Data JPA** with **PostgreSQL** (for Cloud Deployment) and local **H2** fallback:

- **`categories`**: Category name, emoji icon, color hex.
- **`budgets`**: Monthly spending limits per category.
- **`expenses`**: Amount, merchant/description, date, source (`TELEGRAM` or `WEB`), raw text.
- **`alert_logs`**: Warning records with severity (`INFO`, `WARNING`, `CRITICAL`), title, message, and actionable remedies.
- **`user_profiles`**: Telegram chat ID, name, currency, monthly savings target.

---

## 🚀 Running Locally (Zero Configuration Needed)

### 1. Requirements
- **Java 17 or higher**
- **Maven** (or use the included wrapper)

### 2. Start the App
In your terminal, navigate to `c:\expense-tracker` and run:

```bash
mvn spring-boot:run
```

Once started:
- 🌐 **Web Dashboard:** Open [http://localhost:8080](http://localhost:8080)
- 🗄️ **H2 Database Console:** Open [http://localhost:8080/h2-console](http://localhost:8080/h2-console) (JDBC URL: `jdbc:h2:file:./data/expensedb`, Username: `sa`, Password: `password`)

---

## 🤖 Connecting Your Telegram Bot

1. Open Telegram on your phone or PC.
2. Search for **`@BotFather`** and send `/newbot`.
3. Give it a name and a username (e.g., `rohit_expense_bot`).
4. Copy the **HTTP API Token** provided by BotFather.
5. In `src/main/resources/application.properties`, set:
   ```properties
   telegram.bot.username=YOUR_BOT_USERNAME
   telegram.bot.token=YOUR_TELEGRAM_BOT_TOKEN
   ```
   *(Or pass them as environment variables `TELEGRAM_BOT_USERNAME` and `TELEGRAM_BOT_TOKEN`)*.
6. Restart the application. Now message your bot on Telegram:
   - `Lunch 15`
   - `/today`
   - `/afford 120 Shoes`
   - `/summary`

---

## ☁️ Deploying to the Cloud (Render / Railway / Supabase)

### Option A: Railway.app (Easiest 1-Click)
1. Push this folder to a GitHub repository.
2. In Railway, click **New Project** $\rightarrow$ **Deploy from GitHub repo**.
3. Add a **PostgreSQL** database in Railway.
4. Railway automatically sets `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, and `SPRING_DATASOURCE_PASSWORD`.
5. Add your `TELEGRAM_BOT_TOKEN` and `TELEGRAM_BOT_USERNAME` in Environment Variables.

### Option B: Render.com
1. Create a free **PostgreSQL Database** on Render.
2. Copy the **External Database URL**.
3. Create a **Web Service** on Render, choose your repo, and set:
   - **Environment:** Docker (uses the included `Dockerfile`).
   - **Environment Variables:**
     - `SPRING_DATASOURCE_URL` = `jdbc:postgresql://<host>:<port>/<dbname>`
     - `SPRING_DATASOURCE_USERNAME` = `<user>`
     - `SPRING_DATASOURCE_PASSWORD` = `<password>`
     - `SPRING_DATASOURCE_DRIVER` = `org.postgresql.Driver`
     - `TELEGRAM_BOT_TOKEN` = `<your_bot_token>`
     - `TELEGRAM_BOT_USERNAME` = `<your_bot_username>`

---

## 🔌 REST API Endpoints

| Method | Endpoint | Description |
| :--- | :--- | :--- |
| `GET` | `/api/summary` | Overall budget, total spent, safe-to-spend today, category statuses |
| `GET` | `/api/expenses` | List recent expenses |
| `POST` | `/api/expenses` | Create expense (accepts `{ rawText: "Dinner 25" }` or structured JSON) |
| `DELETE`| `/api/expenses/{id}`| Delete a transaction |
| `GET` | `/api/alerts` | List smart velocity and breach warnings |
| `GET` | `/api/afford-check?amount=X&item=Y` | Pre-purchase simulator calculation |
| `POST` | `/api/budgets` | Set or update category monthly budget |
