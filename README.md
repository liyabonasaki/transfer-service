# Transfer Service

**Transfer Service** is a Spring Boot microservice for handling financial transfers between accounts. It communicates with a **Ledger Service** to perform atomic debit and credit operations and supports **idempotent single and batch transfers**.

---

## Features

- Create single transfer with idempotency support  
- Process batch transfers (up to 20 items)  
- Automatic retry/fallback using Resilience4j Circuit Breaker  
- Tracks transfer status (`SUCCESS` / `FAILURE`)  
- H2 in-memory database for development/testing  
- Correlation ID propagation for distributed tracing  

---

## Technologies

- Java 17  
- Spring Boot 3.2.x  
- Spring Web & WebFlux (for WebClient)  
- Spring Data JPA  
- H2 Database  
- Lombok  
- Resilience4j  
- SLF4J / Logback for logging  

---

## Requirements

- Java 17 or higher  
- Maven 3.8+  
- Running **Ledger Service** (default: `http://localhost:8081`)  

---

## Setup

1. **Clone the repository**

```bash
git clone (https://github.com/liyabonasaki/transfer-service.git)
cd transfer-service
