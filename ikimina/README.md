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

3. Update database configuration in `src/main/resources/application.properties`:
   ```properties
   spring.datasource.url=jdbc:postgresql://localhost:5432/ikimina
   spring.datasource.username=your_username
   spring.datasource.password=your_password
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

The application uses Spring Security for authentication and authorization. Two roles are available:
- ADMIN: Full access to all features
- USER: Limited access to personal information and basic operations

Default admin credentials:
- Username: admin
- Password: REDACTED

## Contributing

1. Fork the repository
2. Create a feature branch
3. Commit your changes
4. Push to the branch
5. Create a Pull Request

## License

This project is licensed under the MIT License - see the LICENSE file for details. 