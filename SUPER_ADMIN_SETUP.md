# Super Admin and Group Admin Setup

## Super Admin Initialization

The application automatically creates a super admin user when it starts up for the first time.

### Default Super Admin Credentials:
- **Email**: `superadmin@ikimina.com`
- **Password**: `REDACTED-ROTATED-PASSWORD`
- **Username**: `superadmin`
- **Role**: `ROLE_SUPER_ADMIN`

### How it works:
1. The `DataInitializer` class runs when the application starts
2. It checks if a super admin already exists with the email `superadmin@ikimina.com`
3. If not found, it creates a new super admin with the default credentials
4. The password is encrypted using BCrypt

### Important:
- **Change the password after first login!**
- The super admin has access to all groups and can create new groups
- Only a super admin can create new savings groups

## Group Admin Creation

When a super admin creates a new savings group, a group admin is automatically created for that group.

### Group Admin Credentials:
- **Email**: Provided by the super admin during group creation
- **Password**: Auto-generated (8-character random string + "!")
- **Username**: `admin_[group_name]` (e.g., `admin_ikimina_group`)
- **Role**: `ROLE_GROUP_ADMIN`

### Group Admin Features:
- Can manage only their assigned group
- Can add/remove members from their group
- Can view and manage savings, loans, and fines for their group
- Cannot access other groups' data

### How Group Admins are Created:
1. Super admin fills in the group creation form with:
   - Group name and description
   - Group admin's personal details (name, email, phone)
2. The system:
   - Creates the savings group
   - Creates a group admin user with auto-generated password
   - Links the admin to the group
   - Logs the credentials for the super admin to share

## Login Process

### Super Admin Login:
1. Go to the login page
2. Enter email: `superadmin@ikimina.com`
3. Enter password: `REDACTED-ROTATED-PASSWORD`
4. You'll be redirected to the super admin dashboard

### Group Admin Login:
1. Go to the login page
2. Enter the email provided during group creation
3. Enter the auto-generated password (provided by super admin)
4. You'll be redirected to the group admin dashboard

## Password Management

### First Login:
- Both super admin and group admins should change their password after first login
- Go to Profile → Change Password

### Password Reset:
- Currently, password reset would need to be implemented
- For now, contact the system administrator for password resets

## Security Notes

1. **Default Password**: The super admin default password should be changed immediately in production
2. **Auto-generated Passwords**: Group admin passwords are random but should be changed by users
3. **Role-based Access**: The system enforces strict role-based access control
4. **JWT Tokens**: Authentication uses JWT tokens with expiration

## API Endpoints for Admin Management

### Create Group (Super Admin only):
```
POST /api/savings-groups
{
  "name": "Group Name",
  "description": "Group Description",
  "adminUserEmail": "admin@example.com",
  "adminUserFirstName": "John",
  "adminUserLastName": "Doe",
  "adminUserPhoneNumber": "+250788123456"
}
```

### Get All Groups (Super Admin only):
```
GET /api/savings-groups
```

### Get Group by ID (Group Admin for their group, Super Admin for all):
```
GET /api/savings-groups/{id}
```

## Database Schema

### Users Table:
- `id`: Primary key
- `username`: Unique username
- `email`: Unique email (used for login)
- `password`: Encrypted password
- `first_name`, `last_name`: User's name
- `phone_number`: Contact number
- `role`: USER, GROUP_ADMIN, or SUPER_ADMIN
- `is_active`: Account status

### Savings Groups Table:
- `id`: Primary key
- `name`: Unique group name
- `description`: Group details
- `admin_id`: Foreign key to users table (group admin)
- `is_active`: Group status
- `created_at`, `updated_at`: Timestamps

### Group Members (Many-to-Many):
- Links users to savings groups
- A user can be member of multiple groups
- A group admin is automatically a member of their group
