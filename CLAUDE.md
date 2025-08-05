# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this
repository.

**Build Commands:**

```bash
./mvnw clean spring-javaformat:apply compile                    # Compile application
./mvnw spring-javaformat:apply test                             # Run all tests
```

## Design Requirements
- **Package**: `com.example.softdelete` - Main package

## Implemented Features

### User Management System
- **User Registration & Authentication**: Sign up, login, and logout functionality with magic link authentication
- **Account Management**: Users can view/edit profile information (username, display name, multiple emails with primary designation)
- **Email Management**: Users can add/remove email addresses, set primary email, with proper validation
- **Soft Delete**: Users can delete their own accounts (soft delete implementation)

### Admin Management System
- **Admin Dashboard** (`/admin`): Tabbed interface showing Active/Pending/Deleted users
- **User Promotion**: Admin can promote regular users to admin status with confirmation page
- **User Banning**: Admin can ban users (soft delete) with reason tracking
- **Role-based Access**: Admin-only features protected by Spring Security

### View & URL Conventions
- **View Names**: All user-related views use `user-` prefix (e.g., `user-account`, `user-signup`)
- **URL Patterns**: Use request parameters for variations (e.g., `/delete?confirm`, `/promote?confirm`)
- **Form Handling**: All actions use `form+button` tags with CSRF protection
- **Cancel Pattern**: Cancel operations use `@PostMapping(params = "cancel")` with POST+redirect

### UI/UX Features
- **Responsive Design**: CSS Grid layout for admin dashboard user cards
- **Button Styling**: Consistent button themes (primary, secondary, danger, admin)
- **Email Management UI**: 
  - Multiple email support with "Primary" badges
  - Add/remove email functionality with inline forms
  - Set primary email with confirmation dialogs
  - Success/error message display
- **Tab Navigation**: Admin dashboard with user status filtering
- **Confirmation Pages**: All destructive actions have confirmation steps
- **Form Validation**: Client-side and server-side validation for email operations

### Security Implementation
- **Spring Security**: Form-based authentication with role-based access control
- **CSRF Protection**: All forms include CSRF tokens
- **Access Control**: Admin-only endpoints properly secured
- **Soft Delete Pattern**: Users are banned/deleted, not physically removed

## Development Requirements

### Prerequisites

- Java 21+

### Code Standards

- Use builder pattern if the number of arguments is more than two
- Write javadoc and comments in English
- Spring Java Format enforced via Maven plugin
- All code must pass formatting validation before commit
- Use Java 21 compatible features (avoid Java 22+ specific APIs)
- Use modern Java technics as much as possible like Java Records, Pattern Matching, Text Block etc ...
- Be sure to avoid circular references between classes and packages.
- **Form Pattern**: All buttons use `form+button` tags, avoid `<a>` tags for actions
- **Cancel Pattern**: Use `@PostMapping(path = "/foo", params = "cancel")` for cancel operations
- **Confirmation Pattern**: Use `@GetMapping(params = "confirm")` for confirmation pages

### Architecture Patterns

- **Controllers**: 
  - `UserController` in `com.example.softdelete.user.web` - User account operations, email management
  - `AdminController` in `com.example.softdelete.admin.web` - Admin management operations
- **Services**: 
  - `UserService` - Core user operations (registration, activation, email management, soft delete)
  - `UserMapper` - Database operations with optimized queries
- **DTOs**: Use inner record classes in Controllers for web-specific data transfer
- **Templates**: Mustache templates with consistent naming (`user-*`, `admin-*`)
- **URL Design**: Flat hierarchy with request parameters for variations
- **Performance**: Use in-memory objects (e.g., `ActiveUser.primaryEmail()`) over database queries when possible

### Testing Strategy

- **Unit Tests**: JUnit 5 with AssertJ for service layer testing
- **Integration Tests**: `@SpringBootTest` + Testcontainers (PostgreSQL + Maildev)
- **E2E Tests**: Playwright with Testcontainers for full user journey testing
- **Test Data Management**: Database cleanup after each test using `JdbcClient` to maintain test independence
- **Test Stability**: All tests must pass consistently; use specific Playwright selectors (e.g., `locator("button").getByText()`)
- All tests must pass before completing tasks
- Test coverage includes email management, user registration/activation, admin operations, and soft delete functionality

### After Task completion

- Ensure all code is formatted using `./mvnw spring-javaformat:apply`
- Run full test suite with `./mvnw test`
- For every task, notify that the task is complete and ready for review by the following command:

```
osascript -e 'display notification "<Message Body>" with title "<Message Title>"'
```

## Key Implementation Details

### Email Management System
- **UserService Methods**: `addEmail()`, `removeEmail()`, `setPrimaryEmail()` with proper validation
- **UserMapper**: Optimized database operations, use `updatePrimaryEmail()` method for consistency
- **Controller Endpoints**: 
  - `/account/add-email` - Add new email address
  - `/account/remove-email` - Remove existing email (with primary email protection)
  - `/account/set-primary-email` - Set email as primary
- **Security Context Updates**: Refresh authentication after email changes to reflect updated user state

### Playwright E2E Testing
- **Test Structure**: Use `@SpringBootTest` with Testcontainers for PostgreSQL and Maildev
- **Test Cleanup**: Implement `@AfterEach` with `JdbcClient` to delete users with ID > 14
- **Dialog Handling**: Set up dialog handlers before clicking buttons that trigger confirmations
- **Selector Specificity**: Use specific selectors like `locator("button").getByText("Remove")` to avoid ambiguity
- **Test Data**: Create helper methods like `createUserAndLogin()` and `deleteCurrentUser()` for consistent test setup

### Performance Optimizations
- **Avoid Redundant Queries**: Use `ActiveUser.primaryEmail()` instead of `UserMapper.getCurrentPrimaryEmail()`
- **Batch Database Operations**: Group related database operations in single transactions
- **Test Independence**: Clean up test data after each test to prevent interference

# important-instruction-reminders
Do what has been asked; nothing more, nothing less.
NEVER create files unless they're absolutely necessary for achieving your goal.
ALWAYS prefer editing an existing file to creating a new one.
NEVER proactively create documentation files (*.md) or README files. Only create documentation files if explicitly requested by the User.