-- Drop the existing role check constraint if it exists
ALTER TABLE users DROP CONSTRAINT IF EXISTS users_role_check;

-- Add the correct check constraint for role values
ALTER TABLE users 
ADD CONSTRAINT users_role_check 
CHECK (role IN ('ROLE_SUPER_ADMIN', 'ROLE_GROUP_ADMIN', 'ROLE_USER'));

-- Update any existing records that might have old role values
UPDATE users 
SET role = 'ROLE_SUPER_ADMIN' 
WHERE role = 'SUPER_ADMIN' OR role = 'ADMIN';

UPDATE users 
SET role = 'ROLE_GROUP_ADMIN' 
WHERE role = 'GROUP_ADMIN';

UPDATE users 
SET role = 'ROLE_USER' 
WHERE role = 'USER' AND role NOT LIKE 'ROLE_%';
