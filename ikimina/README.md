# Ikimina - Savings and Loan Management System

Ikimina is a web application built with Spring Boot that helps groups manage their savings (Ingoboka and Ubwizigame), loans, and fines. The system provides role-based access control, allowing administrators to oversee group activities while members can manage their individual accounts.

## Features

- User Management
  - Registration and authentication
  - Role-based access control (Admin and User roles)

- Savings Management
  - Deposit savings (Ingoboka and Ubwizigame)
  - Track savings history
  - View savings balance
  - Generate savings reports (weekly/monthly)

- Loan Management
  - Request loans
  - Track loan status
  - Automatic overdue loan detection
  - Fine generation for late payments

- Fine Management
  - Automatic fine generation for late loan payments
  - Track fine history
  - Calculate total fines

- Reporting
  - Generate periodic reports (weekly/monthly)
  - View savings and fine summaries
  - Admin access to group-wide statistics

## Technical Stack

- Java 17
- Spring Boot 3.2.3
- Spring Security
- Spring Data JPA
- PostgreSQL
- Maven

## Prerequisites

- Java 17 or higher
- PostgreSQL 12 or higher
- Maven 3.6 or higher

## Setup

1. Clone the repository:
   ```bash
   git clone https://github.com/yourusername/ikimina.git
   cd ikimina
   ```

2. Create a PostgreSQL database:
   ```sql
   CREATE DATABASE ikimina;
   ```

3. Supply configuration through the environment. Nothing secret belongs in
   `application.properties` - it is committed. See `.env.example` for the full
   list; these two are required and the application refuses to start without
   them:
   ```bash
   export IKIMINA_DB_PASSWORD=...
   export IKIMINA_JWT_SECRET=$(openssl rand -hex 32)
   ```

4. Build the project:
   ```bash
   mvn clean install
   ```

5. Run the application:
   ```bash
   mvn spring-boot:run
   ```

The application will be available at `http://localhost:8080`

## API Endpoints

### Authentication
- POST `/api/auth/register` - Register a new user

### Savings
- POST `/api/savings` - Create a new savings entry
- GET `/api/savings/user/{userId}` - Get user's savings history
- GET `/api/savings/user/{userId}/total` - Get user's total savings between dates
- GET `/api/savings/daily-total` - Get total savings for a specific date (Admin only)

### Loans
- POST `/api/loans/request` - Request a new loan
- PUT `/api/loans/{loanId}/status` - Update loan status (Admin only)
- GET `/api/loans/user/{userId}` - Get user's loans
- GET `/api/loans/overdue` - Get overdue loans (Admin only)
- POST `/api/loans/check-overdue` - Check and update overdue loans (Admin only)

### Fines
- POST `/api/fines` - Create a new fine (Admin only)
- GET `/api/fines/user/{userId}` - Get user's fines
- GET `/api/fines/user/{userId}/total` - Get user's total fines between dates

### Reports
- POST `/api/reports/generate/{userId}` - Generate a report
- GET `/api/reports/user/{userId}` - Get user's reports
- GET `/api/reports/user/{userId}/period` - Get user's reports by period

## Security

The application uses Spring Security with JWT bearer tokens. Three roles exist:

- `ROLE_SUPER_ADMIN` - manages groups, subscriptions and the audit trail
- `ROLE_GROUP_ADMIN` - manages one group: members, savings, loans, fines
- `ROLE_USER` - a member; sees only their own records

Endpoints carrying a `{userId}` or `{groupId}` are checked at the object level,
not just by role, so a member cannot read another member's records by changing
the id in the URL.

### Credentials

There are no default credentials, and none are stored in this repository.

On first boot the super admin is created from the environment:

- `IKIMINA_SUPERADMIN_EMAIL` (default `superadmin@ikimina.com`)
- `IKIMINA_SUPERADMIN_PASSWORD` - if unset, a random password is generated and
  printed **once** to the log at `WARN`. Capture it and change it immediately.

Group admin accounts are provisioned when a super admin creates a group; the
one-time password is returned in that API response and is never logged.

> Earlier revisions of this file documented a default `admin` / `admin` login,
> backed by `spring.security.user.*` in `application.properties`. Both have been
> removed and that login no longer works. It remains in this repository's git
> history, which is public, so treat it as disclosed.

## Contributing

1. Fork the repository
2. Create a feature branch
3. Commit your changes
4. Push to the branch
5. Create a Pull Request

## License

This project is licensed under the MIT License - see the LICENSE file for details. 