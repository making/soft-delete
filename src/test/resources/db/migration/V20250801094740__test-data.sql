-- Test Data for User Tables
-- This script creates sample data using INSERT statements
-- Using RFC 2606 compliant test domains

-- Insert base users
INSERT INTO users DEFAULT
VALUES; -- user_id 1
INSERT INTO users DEFAULT
VALUES; -- user_id 2
INSERT INTO users DEFAULT
VALUES; -- user_id 3
INSERT INTO users DEFAULT
VALUES; -- user_id 4
INSERT INTO users DEFAULT
VALUES; -- user_id 5
INSERT INTO users DEFAULT
VALUES; -- user_id 6
INSERT INTO users DEFAULT
VALUES; -- user_id 7
INSERT INTO users DEFAULT
VALUES; -- user_id 8
INSERT INTO users DEFAULT
VALUES; -- user_id 9
INSERT INTO users DEFAULT
VALUES; -- user_id 10
INSERT INTO users DEFAULT
VALUES; -- user_id 11
INSERT INTO users DEFAULT
VALUES; -- user_id 12
INSERT INTO users DEFAULT
VALUES; -- user_id 13
INSERT INTO users DEFAULT
VALUES;
-- user_id 14

-- Insert user profiles
INSERT INTO user_profiles (user_id, username, display_name, created_at)
VALUES (1, 'johndoe', 'John Doe', '2024-01-15 10:00:00+00'),
    (2, 'janesminth', 'Jane Smith', '2024-01-16 11:30:00+00'),
    (3, 'mikebrown', 'Mike Brown', '2024-01-17 09:15:00+00'),
    (4, 'sarahjones', 'Sarah Jones', '2024-01-18 14:20:00+00'),
    (5, 'alexwilson', 'Alex Wilson', '2024-01-19 16:45:00+00'),
    (6, 'emilydavis', 'Emily Davis', '2024-01-20 08:30:00+00'),
    (7, 'tomtaylor', 'Tom Taylor', '2024-01-21 13:10:00+00'),
    (8, 'lisawhite', 'Lisa White', '2024-01-22 15:25:00+00'),
    (9, 'baduser1', 'Bad User One', '2024-01-23 12:00:00+00'),
    (10, 'baduser2', 'Bad User Two', '2024-01-24 10:30:00+00'),
    (11, 'testaccount', 'Test Account', '2024-01-25 09:45:00+00'),
    (12, 'violator', 'Rule Violator', '2024-01-26 11:15:00+00'),
    (13, 'pendinguser1', 'Pending User One', '2024-01-27 14:00:00+00'),
    (14, 'pendinguser2', 'Pending User Two', '2024-01-28 16:30:00+00');

-- Insert pending users (not yet activated)
INSERT INTO pending_users (user_id, activation_token, expires_at, created_at)
VALUES (13, 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11'::UUID, '2024-03-01 14:00:00+00',
        '2024-01-27 14:00:00+00'),
    (14, 'b1ffcd88-8b1a-3de7-aa5c-5aa8ac290b22'::UUID, '2024-03-01 16:30:00+00',
     '2024-01-28 16:30:00+00');

-- Insert active users
INSERT INTO active_users (user_id, activated_at)
VALUES (1, '2024-01-15 10:15:00+00'),
    (2, '2024-01-16 11:45:00+00'),
    (3, '2024-01-17 09:30:00+00'),
    (4, '2024-01-18 14:35:00+00'),
    (5, '2024-01-19 17:00:00+00'),
    (6, '2024-01-20 08:45:00+00'),
    (7, '2024-01-21 13:25:00+00'),
    (8, '2024-01-22 15:40:00+00'),
    (9, '2024-01-23 12:15:00+00'),
    (10, '2024-01-24 10:45:00+00'),
    (11, '2024-01-25 10:00:00+00'),
    (12, '2024-01-26 11:30:00+00');

-- Insert all emails (including for pending users)
INSERT INTO user_emails (email, user_id, created_at)
VALUES ('john.doe@example.com', 1, '2024-01-15 10:00:00+00'),
    ('jane.smith@example.org', 2, '2024-01-16 11:30:00+00'),
    ('mike.brown@example.net', 3, '2024-01-17 09:15:00+00'),
    ('sarah.jones@test.example', 4, '2024-01-18 14:20:00+00'),
    ('alex.wilson@example.com', 5, '2024-01-19 16:45:00+00'),
    ('emily.davis@example.org', 6, '2024-01-20 08:30:00+00'),
    ('tom.taylor@example.net', 7, '2024-01-21 13:10:00+00'),
    ('lisa.white@test.example', 8, '2024-01-22 15:25:00+00'),
    ('bad.user1@example.com', 9, '2024-01-23 12:00:00+00'),
    ('bad.user2@example.org', 10, '2024-01-24 10:30:00+00'),
    ('test@example.net', 11, '2024-01-25 09:45:00+00'),
    ('violator@test.example', 12, '2024-01-26 11:15:00+00'),
    ('pending1@example.com', 13, '2024-01-27 14:00:00+00'),
    ('pending2@example.org', 14, '2024-01-28 16:30:00+00');

-- Set primary emails for all users
INSERT INTO user_primary_emails (user_id, email)
VALUES (1, 'john.doe@example.com'),
    (2, 'jane.smith@example.org'),
    (3, 'mike.brown@example.net'),
    (4, 'sarah.jones@test.example'),
    (5, 'alex.wilson@example.com'),
    (6, 'emily.davis@example.org'),
    (7, 'tom.taylor@example.net'),
    (8, 'lisa.white@test.example'),
    (9, 'bad.user1@example.com'),
    (10, 'bad.user2@example.org'),
    (11, 'test@example.net'),
    (12, 'violator@test.example'),
    (13, 'pending1@example.com'),
    (14, 'pending2@example.org');

-- Add additional emails for some users
INSERT INTO user_emails (email, user_id, created_at)
VALUES ('john.doe.work@example.org', 1, '2024-01-16 14:00:00+00'),
    ('j.doe@example.net', 1, '2024-01-17 16:30:00+00'),
    ('jane@example.com', 2, '2024-01-18 10:15:00+00'),
    ('mike.brown.dev@example.org', 3, '2024-01-19 12:45:00+00'),
    ('alex@test.example', 5, '2024-01-20 15:20:00+00');

-- Promote some users to admin
INSERT INTO admin_users (user_id)
VALUES (1), -- John Doe becomes admin
    (3);
-- Mike Brown becomes admin

-- Update primary emails for users with multiple addresses
UPDATE user_primary_emails
SET email = 'john.doe.work@example.org'
WHERE user_id = 1;
UPDATE user_primary_emails
SET email = 'alex@test.example'
WHERE user_id = 5;

-- Self-deletion scenarios
-- Record the deletion events with JSON user info (no deletion_reason)
INSERT INTO user_deletion_events (user_id, user_info_at_deletion, deleted_at)
VALUES (11,
        '{"username": "testaccount", "displayName": "Test Account", "primaryEmail": "test@example.net", "activatedAt": "2024-01-25T10:00:00+00:00", "emails": ["test@example.net"]}',
        '2024-02-01 10:00:00+00'),
    (12,
     '{"username": "violator", "displayName": "Rule Violator", "primaryEmail": "violator@test.example", "activatedAt": "2024-01-26T11:30:00+00:00", "emails": ["violator@test.example"]}',
     '2024-02-02 14:30:00+00');

-- BAN scenarios (admin actions)
INSERT INTO user_ban_events (user_id, admin_user_id, user_info_at_ban, ban_reason, banned_at)
VALUES (9, 1,
        '{"username": "baduser1", "displayName": "Bad User One", "primaryEmail": "bad.user1@example.com", "activatedAt": "2024-01-23T12:15:00+00:00", "emails": ["bad.user1@example.com"]}',
        'Repeatedly sent spam messages to other users', '2024-02-03 09:15:00+00'),
    (10, 3,
     '{"username": "baduser2", "displayName": "Bad User Two", "primaryEmail": "bad.user2@example.org", "activatedAt": "2024-01-24T10:45:00+00:00", "emails": ["bad.user2@example.org"]}',
     'Posted inappropriate content multiple times', '2024-02-04 11:45:00+00');

-- Remove deleted users from active state and move to deleted_users
DELETE
FROM active_users
WHERE user_id IN (9, 10, 11, 12);
DELETE
FROM user_profiles
WHERE user_id IN (9, 10, 11, 12);
DELETE
FROM user_primary_emails
WHERE user_id IN (9, 10, 11, 12);
DELETE
FROM user_emails
WHERE user_id IN (9, 10, 11, 12);

INSERT INTO deleted_users (user_id, deleted_at)
VALUES (9, '2024-02-03 09:15:00+00'),
    (10, '2024-02-04 11:45:00+00'),
    (11, '2024-02-01 10:00:00+00'),
    (12, '2024-02-02 14:30:00+00');