# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

This is a bidirectional approval management system (审批管理系统) with mobile and admin interfaces. Users can create approval requests for each other only when they have an established mutual relationship (互为对象). The system supports image uploads to Aliyun OSS, SMS/email notifications, and detailed operation timeline tracking.

Key concept: Users must first establish a mutual "relation" (对象关系) before they can create approval requests for each other. This is a bidirectional permission model enforced at the service layer.

## Common Commands

### Database Setup
```bash
mysql -u root -p < database.sql
```

### Backend Development
```bash
cd backend
mvn clean install              # Build project
mvn spring-boot:run           # Run backend server (port 8080)
mvn test                      # Run tests
```

### Frontend Development
```bash
cd frontend
npm install                   # Install dependencies
npm run dev                   # Start dev server
npm run build                 # Production build
npm run preview               # Preview production build
npm run lint                  # Lint TypeScript/Vue files
npm run openapi               # Generate TypeScript types from OpenAPI spec
```

## Architecture

### Authentication & Authorization

**JWT-based authentication** with Spring Security:
- JWT tokens generated on login at `AuthController`
- `JwtAuthenticationFilter` (common/security/) validates tokens and sets SecurityContext
- User ID stored in SecurityContext principal for request handling
- Public endpoints: `/api/auth/login`, `/api/auth/register`, `/api/health`, Swagger UI

**Critical permission model**: The `UserRelationService` enforces that users can only create/approve applications for users they have a mutual relation with (relation_type=2 in user_relations table). Always validate relations before application operations.

### Service Layer Design

Services follow interface + implementation pattern:
- Interfaces in `service/` package (e.g., `IApplicationService`, `IUserRelationService`)
- Implementations in `service/impl/` (e.g., `ApplicationServiceImpl`)
- Core services:
  - `IApplicationService`: Create/approve/reject applications
  - `IUserRelationService`: Manage mutual user relations (发起申请/接受/拒绝)
  - `INotificationService`: Async SMS/email notifications
  - `IOperationLogService`: Timeline tracking for all application operations

### Application Lifecycle & Status Flow

Applications (applications table) flow through these states (ApplicationStatusEnum):
1. **PENDING (1)**: Awaiting approval
2. **APPROVED (2)**: Approved by approver
3. **REJECTED (3)**: Rejected by approver
4. **DRAFT (4)**: Draft state (if used)

Each state change creates an `operation_logs` record with OperationType:
- CREATE (1), APPROVE (2), REJECT (3), UPDATE (4), CANCEL (5)

### File Upload Architecture

**Two separate attachment tables**:
- `application_attachments`: Files uploaded when creating applications
- `approval_attachments`: Files uploaded during approval process (linked to operation_log_id)

Files are stored in Aliyun OSS via `OssUtils` (common/utils/). The service layer separates concerns with `IApplicationAttachmentService` and `IApprovalAttachmentService`.

### Frontend Architecture

**Dual UI approach**:
- Mobile UI: Vant components (`pages/mobile/`), bottom navigation, optimized for touch
- Admin UI: Arco Design components (`pages/admin/`), desktop-focused management

**Routing structure**:
- `/mobile/*`: Mobile interface with nested routes (applications, approvals, relations, profile)
- `/admin/*`: Admin interface (dashboard, applications, users, notifications)
- Route guard checks `userStore.token` for authentication

**State Management**:
- Pinia stores in `store/modules/` (e.g., `user.ts`)
- API calls centralized in `services/api.ts`
- HTTP client configured in `services/http.ts` with interceptors for JWT token injection

### Notification System

Asynchronous notification system:
- `INotificationService` sends SMS (Aliyun) and email (SMTP via Hutool)
- `NotificationScheduler` handles scheduled/retry logic
- `notifications` table tracks delivery status (PENDING/SENT/FAILED)
- Notifications triggered on application creation and approval/rejection
- Voice notifications also available via `IVoiceNotificationService`

## Configuration Requirements

Before running the application, configure `backend/src/main/resources/application.yml`:

**Required configurations**:
- Database: `spring.datasource.url`, username, password (MySQL 8.0+)
- JWT: `jwt.secret` (must be 256+ bits), `jwt.expiration`
- Aliyun OSS: `aliyun.oss.endpoint`, access keys, bucket name, bucket URL
- Aliyun SMS: `aliyun.sms.access-key-id`, secret, sign-name, template-code
- Email (optional): SMTP configuration for email notifications

## Key Patterns & Conventions

**MyBatis Plus**:
- Entities in `entity/` package extend BaseEntity or use standard annotations
- Mappers in `mapper/` package extend `BaseMapper<Entity>`
- MyBatisPlusConfig enables pagination interceptor

**DTOs for API contracts**:
- Request DTOs in `dto/` package (e.g., `ApplicationCreateRequest`, `ApplicationApprovalRequest`)
- Validation annotations (@NotNull, @NotBlank) on DTO fields
- Response wrapped in `ApiResponse<T>` (common/response/)

**Exception handling**:
- Global exception handler in `common/exception/GlobalExceptionHandler`
- Custom exceptions thrown from service layer
- Returns standardized error responses to frontend

**Frontend TypeScript**:
- Full TypeScript usage with `<script setup lang="ts">`
- API types generated from OpenAPI spec via `npm run openapi`
- Type definitions in `.d.ts` files

## Database Schema Notes

**Core tables**:
- `users`: User accounts (username, phone, email unique)
- `user_relations`: Mutual relations with requester tracking
  - Unique constraint on (user_id, related_user_id)
  - relation_type: 1=pending, 2=established
- `applications`: Approval requests (applicant_id, approver_id, status)
- `operation_logs`: Complete audit trail for applications
- `application_attachments` / `approval_attachments`: Separate file tracking

**Important indexes**:
- Applications: Composite indexes on (applicant_id, approver_id) and (approver_id, status)
- User relations: Indexed on both user_id and related_user_id for bidirectional queries

## Development Workflow

When adding new features:
1. Add/modify entity classes (with MyBatis Plus annotations)
2. Create/update Mapper interfaces extending BaseMapper
3. Define service interface in `service/` package
4. Implement service in `service/impl/` with business logic
5. Create DTO classes for request/response if needed
6. Add Controller methods with proper validation and error handling
7. Update frontend API service in `services/api.ts`
8. Create/update Vue components using appropriate UI library (Vant for mobile, Arco for admin)
9. Run `npm run openapi` to regenerate TypeScript types from backend API

When modifying authentication or relations:
- Remember the mutual relation enforcement - validate with `IUserRelationService.isRelated()`
- Update JWT filter in `JwtAuthenticationFilter` if changing auth paths
- Consider impact on both mobile and admin UIs