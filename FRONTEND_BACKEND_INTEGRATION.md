# Ikimina Frontend-Backend Integration Guide

## Architecture Overview

### Frontend (React + Vite)
- **Port**: 3000
- **Framework**: React 18 with Vite
- **State Management**: Redux Toolkit
- **API Client**: RTK Query
- **Styling**: Tailwind CSS
- **Routing**: React Router v7

### Backend (Spring Boot)
- **Port**: 8082
- **Framework**: Spring Boot 3.x
- **Database**: PostgreSQL
- **Security**: JWT Authentication
- **Architecture**: RESTful API

## Integration Configuration

### 1. Vite Proxy Configuration
```javascript
// vite.config.js
server: {
  port: 3000,
  proxy: {
    '/api': {
      target: 'http://localhost:8082',
      changeOrigin: true,
      secure: false,
      rewrite: (path) => path.replace(/^\/api/, ''),
    },
  },
}
```

### 2. Frontend API Configuration
```javascript
// src/app/api/apiSlice.js
const API_URL = '/api'; // Uses Vite proxy in development
```

### 3. Backend CORS Configuration
```java
// SecurityConfig.java
@Bean
public CorsConfigurationSource corsConfigurationSource() {
    CorsConfiguration configuration = new CorsConfiguration();
    configuration.setAllowedOrigins(Arrays.asList("http://localhost:3000", "http://localhost:3001"));
    configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
    configuration.setAllowedHeaders(Arrays.asList("Authorization", "Content-Type", "Accept", "x-auth-token"));
    configuration.setAllowCredentials(true);
    // ...
}
```

## API Endpoints Mapping

| Feature | Frontend Hook | Backend Endpoint | Method |
|---------|---------------|------------------|--------|
| Authentication | `useLoginMutation` | `/api/auth/login` | POST |
| Registration | `useRegisterMutation` | `/api/auth/register` | POST |
| Get Savings | `useGetSavingsQuery` | `/api/savings/user/{userId}` | GET |
| Create Savings | `useCreateSavingMutation` | `/api/savings` | POST |
| Get Loans | `useGetLoansQuery` | `/api/loans/user/{userId}` | GET |
| Request Loan | `useRequestLoanMutation` | `/api/loans` | POST |
| Get Fines | `useGetFinesQuery` | `/api/fines/user/{userId}` | GET |
| Get Groups | `useGetAllGroupsQuery` | `/api/savings-groups` | GET |
| Create Group | `useCreateSavingsGroupMutation` | `/api/savings-groups` | POST |
| Get User Profile | `useGetUserProfileQuery` | `/api/users/{userId}` | GET |
| Generate Report | `useGenerateReportMutation` | `/api/reports/generate` | POST |
| Get Transactions | `useGetTransactionsQuery` | `/api/transactions/user/{userId}` | GET |
| Current Cycle | `useGetCurrentCycleQuery` | `/api/savings-cycles/current/{groupId}` | GET |
| Start Cycle | `useStartNewCycleMutation` | `/api/savings-cycles/start/{groupId}` | POST |
| Distribute Savings | `useDistributeSavingsMutation` | `/api/savings-cycles/distribute/{groupId}` | POST |
| Get Subscription | `useGetSubscriptionQuery` | `/api/subscriptions/group/{groupId}` | GET |
| Create Subscription | `useCreateSubscriptionMutation` | `/api/subscriptions` | POST |
| All Users (Admin) | `useGetAllUsersQuery` | `/api/admin/users` | GET |
| Audit Logs | `useGetAuditLogsQuery` | `/api/admin/audit-logs` | POST |
| Bulk Savings | `useBulkCreateSavingsMutation` | `/api/savings/bulk` | POST |
| Savings Ledger | `useGetMemberSavingsLedgerQuery` | `/api/savings/ledger` | POST |

## Authentication Flow

1. **Login Request**:
   ```javascript
   const [login] = useLoginMutation();
   const result = await login({
     email: 'user@example.com',
     password: 'password',
     savingsGroupId: 1
   });
   ```

2. **Backend Response**:
   ```json
   {
     "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
     "type": "Bearer",
     "user": {
       "id": 1,
       "email": "user@example.com",
       "firstName": "John",
       "lastName": "Doe",
       "role": "ROLE_USER",
       "memberNumber": "M001",
       "savingsGroupId": 1
     }
   }
   ```

3. **Token Storage**:
   - JWT token stored in localStorage
   - Automatically included in all subsequent API requests
   - Token sent in `Authorization: Bearer <token>` header

## Role-Based Access Control

### Frontend Routes
- **Public**: `/login`, `/register`
- **Authenticated**: `/dashboard/*`
- **Super Admin Only**: `/admin/*`, `/groups`

### Navigation by Role
- **ROLE_USER**: My Profile, Savings, Loans, Fines
- **ROLE_GROUP_ADMIN**: Members, Savings, Loans, Distribution, Reports
- **ROLE_SUPER_ADMIN**: All menus + Groups + Super Admin panel

## Error Handling

### Frontend
- 401 responses automatically clear token and redirect to login
- API errors displayed using react-toastify
- RTK Query provides caching and optimistic updates

### Backend
- Global exception handler
- Validation errors return 400 with details
- Authorization errors return 403
- Authentication errors return 401

## Testing the Integration

1. **Start Backend**:
   ```bash
   cd ikimina
   mvn spring-boot:run
   ```

2. **Start Frontend**:
   ```bash
   cd frontend-ikimina
   npm run dev
   ```

3. **Test in Browser**:
   - Open http://localhost:3000
   - Open browser console (F12)
   - Run: `testBackendIntegration()`

## Common Issues and Solutions

### CORS Issues
- Ensure backend allows frontend origin
- Check that credentials are included in requests
- Verify proxy configuration in vite.config.js

### Authentication Issues
- Check JWT secret matches between frontend and backend
- Verify token expiration time
- Ensure role strings match exactly

### API Endpoint Issues
- Remove duplicate `/api` prefixes in URLs
- Check that backend controllers have correct `@RequestMapping`
- Verify HTTP methods match between frontend and backend

## Development Best Practices

1. **Environment Variables**:
   - Use `.env` files for different environments
   - Store sensitive data in environment variables

2. **API Versioning**:
   - Consider versioning APIs (e.g., `/api/v1/`)
   - Keep backward compatibility when possible

3. **Error Logging**:
   - Log API errors on both frontend and backend
   - Use correlation IDs for request tracking

4. **Performance**:
   - Use RTK Query caching effectively
   - Implement pagination for large datasets
   - Consider lazy loading for heavy components
