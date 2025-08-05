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
- **User Registration & Authentication**: Sign up, login, and logout functionality
- **Account Management**: Users can view/edit profile information (username, display name, multiple emails with primary designation)
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
- **Email Display**: Multiple email support with "Primary" badges
- **Tab Navigation**: Admin dashboard with user status filtering
- **Confirmation Pages**: All destructive actions have confirmation steps

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
  - `UserController` in `com.example.softdelete.user.web` - User account operations
  - `AdminController` in `com.example.softdelete.admin.web` - Admin management operations
- **DTOs**: Use inner record classes in Controllers for web-specific data transfer
- **Services**: Direct use of `UserService` and `UserMapper`, no intermediate Admin services
- **Templates**: Mustache templates with consistent naming (`user-*`, `admin-*`)
- **URL Design**: Flat hierarchy with request parameters for variations

### Testing Strategy

- JUnit 5 with AssertJ
- Integration tests using `@SpringBootTest` + Testcontainers
- All tests must pass before completing tasks
- Code examples in the README must be tested to ensure they work.

### After Task completion

- Ensure all code is formatted using `./mvnw spring-javaformat:apply`
- Run full test suite with `./mvnw test`
- For every task, notify that the task is complete and ready for review by the following command:

```
osascript -e 'display notification "<Message Body>" with title "<Message Title>"’
```