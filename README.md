# WalletX — Digital Wallet Backend

WalletX is a RESTful backend application built with Java and Spring Boot that simulates basic digital wallet operations. It supports wallet creation, deposits, money transfers, and transaction history.

**Note:** This is a portfolio project for learning backend development. It does not process real money or integrate with actual payment providers.

## Features

- Create and retrieve wallets
- Deposit money into a wallet
- Transfer money between wallets
- View paginated transaction history
- Validate request data and handle API errors
- Use database transactions to maintain consistency
- Apply pessimistic locking to help prevent concurrent transfer issues
- Test business logic and API endpoints

## Tech Stack

- **Language:** Java 21
- **Framework:** Spring Boot 4.1.1
- **Database:** PostgreSQL
- **Persistence:** Spring Data JPA / Hibernate
- **Build Tool:** Maven
- **Testing:** JUnit, Mockito, Spring Boot Test, H2
- **API Testing:** VS Code REST Client

## Getting Started

### Prerequisites

- Java 21
- PostgreSQL
- Git

### 1. Clone the repository

```bash
git clone https://github.com/mdyasirarafat7/WalletX.git
cd WalletX
```

### 2. Create the database

Create a PostgreSQL database named `walletx_db`.

Configure the database URL, username, and password using the project's `src/main/resources/application.properties` file and environment variables.

Example environment variables in PowerShell:

```powershell
$env:DB_USERNAME = "postgres"
$env:DB_PASSWORD = "your_database_password"
```

Replace the example values with your local PostgreSQL credentials. Do not commit real credentials to Git.

### 3. Run the application

```powershell
.\mvnw.cmd spring-boot:run
```

The API will normally be available at:

`http://localhost:8080`

### 4. Run the tests

```powershell
.\mvnw.cmd test
```

The test configuration uses an H2 in-memory database so tests can run separately from the development PostgreSQL database.

## API Endpoints

| Method | Endpoint | Description |
|---|---|---|
| POST | `/api/wallets` | Create a wallet |
| GET | `/api/wallets` | List wallets |
| GET | `/api/wallets/{id}` | Get a wallet by ID |
| POST | `/api/wallets/{id}/deposit` | Deposit money |
| POST | `/api/wallets/{id}/transfer` | Transfer money |
| GET | `/api/wallets/{id}/transactions` | Get transaction history |

Transaction history supports pagination through query parameters such as `page` and `size`.

## Example Requests

### Create a wallet

```http
POST /api/wallets
Content-Type: application/json

{
  "ownerName": "Alice"
}
```

### Deposit money

```http
POST /api/wallets/1/deposit
Content-Type: application/json

{
  "amount": 100.00
}
```

### Transfer money

```http
POST /api/wallets/1/transfer
Content-Type: application/json

{
  "toWalletId": 2,
  "amount": 25.00
}
```

These examples assume the relevant wallets exist and the sender has sufficient funds. The exact request and response schemas are defined in the source code.

## Engineering Highlights

- Uses `BigDecimal` for monetary values.
- Separates controller, service, repository, entity, and DTO responsibilities.
- Uses transactional service methods to keep balance changes and transaction records consistent.
- Uses database locking to help coordinate concurrent transfers.
- Includes unit and integration tests for important application behavior.

## Current Limitations

- No user authentication or authorization yet.
- No real payment gateway integration.
- No production deployment configuration yet.

## Future Improvements

- Add Spring Security and user authentication.
- Restrict wallet access to authorized users.
- Add API documentation with Swagger/OpenAPI.
- Add Docker support and deployment.
- Expand integration testing against PostgreSQL.

## Author

**Md. Yasir Arafat**

GitHub: https://github.com/mdyasirarafat7
