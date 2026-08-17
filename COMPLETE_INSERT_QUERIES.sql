-- ============================================
-- COMPLETE INSERT QUERIES - READY TO USE
-- ============================================

-- ============================================
-- 1. TWILIO NUMBERS - INSERT QUERIES
-- ============================================

-- Single Number Insert
INSERT INTO twilio_numbers 
    (phone_number, friendly_name, is_active, in_use, status, country_code, description, created_at, updated_at)
VALUES 
    ('+19452707778', 'Primary Support Line', true, false, 'available', 'US', 'Main customer support line', NOW(), NOW());

-- Multiple Numbers (Bulk Insert)
INSERT INTO twilio_numbers 
    (phone_number, friendly_name, is_active, in_use, status, country_code, description, created_at, updated_at)
VALUES 
    ('+19452707778', 'Sales Line 1', true, false, 'available', 'US', 'Sales department - Line 1', NOW(), NOW()),
    ('+19452707772', 'Sales Line 2', true, false, 'available', 'US', 'Sales department - Line 2', NOW(), NOW()));


-- ============================================
-- 2. APP_CONFIG - DASHBOARD MESSAGE SETUP
-- ============================================

-- Initialize Default Configuration (Run this first)
INSERT INTO app_config 
    (config_key, config_value, config_type, description, is_enabled, created_at, updated_at)
VALUES 
    ('dashboard_message', '', 'STRING', 'Dashboard announcement message', true, NOW(), NOW()),
    ('dashboard_message_enabled', 'false', 'BOOLEAN', 'Enable/disable dashboard message', true, NOW(), NOW())
ON DUPLICATE KEY UPDATE 
    updated_at = NOW();


-- ============================================
-- 3. SAMPLE DASHBOARD MESSAGES (Choose One)
-- ============================================

-- Option 1: Welcome Message
UPDATE app_config SET config_value = '👋 Welcome to Phone Booth! Your trusted calling solution.', updated_at = NOW() WHERE config_key = 'dashboard_message';
UPDATE app_config SET config_value = 'true', updated_at = NOW() WHERE config_key = 'dashboard_message_enabled';

-- Option 2: Maintenance Notice
-- UPDATE app_config SET config_value = '🔧 Scheduled maintenance tonight 10 PM - 11 PM. Service may be briefly interrupted.', updated_at = NOW() WHERE config_key = 'dashboard_message';
-- UPDATE app_config SET config_value = 'true', updated_at = NOW() WHERE config_key = 'dashboard_message_enabled';

-- Option 3: Promotion
-- UPDATE app_config SET config_value = '🎁 Special Offer! Get 20% extra balance on all recharges this week.', updated_at = NOW() WHERE config_key = 'dashboard_message';
-- UPDATE app_config SET config_value = 'true', updated_at = NOW() WHERE config_key = 'dashboard_message_enabled';

-- Option 4: New Feature
-- UPDATE app_config SET config_value = '🎉 New Feature: View detailed call history with duration and costs!', updated_at = NOW() WHERE config_key = 'dashboard_message';
-- UPDATE app_config SET config_value = 'true', updated_at = NOW() WHERE config_key = 'dashboard_message_enabled';

-- Option 5: Important Notice
-- UPDATE app_config SET config_value = '⚠️ Important: Please update your account information by end of this month.', updated_at = NOW() WHERE config_key = 'dashboard_message';
-- UPDATE app_config SET config_value = 'true', updated_at = NOW() WHERE config_key = 'dashboard_message_enabled';


-- ============================================
-- 4. ADDITIONAL APP_CONFIG EXAMPLES
-- ============================================

-- Maintenance Mode Flag
INSERT INTO app_config 
    (config_key, config_value, config_type, description, is_enabled, created_at, updated_at)
VALUES 
    ('maintenance_mode', 'false', 'BOOLEAN', 'Enable maintenance mode', true, NOW(), NOW())
ON DUPLICATE KEY UPDATE updated_at = NOW();

-- Max Concurrent Calls
INSERT INTO app_config 
    (config_key, config_value, config_type, description, is_enabled, created_at, updated_at)
VALUES 
    ('max_concurrent_calls', '10', 'INTEGER', 'Maximum concurrent calls allowed', true, NOW(), NOW())
ON DUPLICATE KEY UPDATE updated_at = NOW();

-- Call Rate Configuration
INSERT INTO app_config 
    (config_key, config_value, config_type, description, is_enabled, created_at, updated_at)
VALUES 
    ('call_rate_per_minute', '10.00', 'DECIMAL', 'Call rate per minute in rupees', true, NOW(), NOW())
ON DUPLICATE KEY UPDATE updated_at = NOW();

-- Minimum Wallet Balance
INSERT INTO app_config 
    (config_key, config_value, config_type, description, is_enabled, created_at, updated_at)
VALUES 
    ('minimum_wallet_balance', '5.00', 'DECIMAL', 'Minimum wallet balance required to make calls', true, NOW(), NOW())
ON DUPLICATE KEY UPDATE updated_at = NOW();

-- Support Contact Info
INSERT INTO app_config 
    (config_key, config_value, config_type, description, is_enabled, created_at, updated_at)
VALUES 
    ('support_email', 'support@phonebooth.com', 'STRING', 'Support email address', true, NOW(), NOW()),
    ('support_phone', '+1234567890', 'STRING', 'Support phone number', true, NOW(), NOW())
ON DUPLICATE KEY UPDATE updated_at = NOW();


-- ============================================
-- 5. QUICK VERIFICATION QUERIES
-- ============================================

-- Check Twilio Numbers
SELECT phone_number, friendly_name, is_active, in_use, status 
FROM twilio_numbers
ORDER BY created_at DESC;

-- Count Available Numbers
SELECT 
    COUNT(*) as total_numbers,
    SUM(CASE WHEN is_active = true AND in_use = false THEN 1 ELSE 0 END) as available_numbers,
    SUM(CASE WHEN in_use = true THEN 1 ELSE 0 END) as in_use_numbers
FROM twilio_numbers;

-- Check Dashboard Message Configuration
SELECT 
    config_key,
    config_value,
    CASE 
        WHEN config_key = 'dashboard_message_enabled' AND config_value = 'true' 
        THEN '✅ Enabled'
        WHEN config_key = 'dashboard_message_enabled' AND config_value = 'false'
        THEN '❌ Disabled'
        ELSE config_value
    END AS 'Display Status'
FROM app_config
WHERE config_key IN ('dashboard_message', 'dashboard_message_enabled')
ORDER BY config_key;

-- View All Configurations
SELECT 
    config_key,
    config_value,
    config_type,
    description,
    is_enabled
FROM app_config
ORDER BY config_key;


-- ============================================
-- 6. QUICK START - MINIMUM SETUP
-- ============================================

-- Run these 3 queries to get started quickly:

-- 1. Add one Twilio number
INSERT INTO twilio_numbers (phone_number, friendly_name, is_active, in_use, status, created_at, updated_at)
VALUES ('+19452707778', 'Primary Line', true, false, 'available', NOW(), NOW());

-- 2. Initialize dashboard config
INSERT INTO app_config (config_key, config_value, config_type, description, is_enabled, created_at, updated_at)
VALUES 
    ('dashboard_message', '', 'STRING', 'Dashboard announcement', true, NOW(), NOW()),
    ('dashboard_message_enabled', 'false', 'BOOLEAN', 'Enable dashboard message', true, NOW(), NOW())
ON DUPLICATE KEY UPDATE updated_at = NOW();

-- 3. Set welcome message
UPDATE app_config SET config_value = '👋 Welcome to Phone Booth!', updated_at = NOW() WHERE config_key = 'dashboard_message';
UPDATE app_config SET config_value = 'true', updated_at = NOW() WHERE config_key = 'dashboard_message_enabled';


-- ============================================
-- 7. DISABLE/ENABLE DASHBOARD MESSAGE
-- ============================================

-- To Disable Dashboard Message
-- UPDATE app_config SET config_value = 'false', updated_at = NOW() WHERE config_key = 'dashboard_message_enabled';

-- To Enable Dashboard Message
-- UPDATE app_config SET config_value = 'true', updated_at = NOW() WHERE config_key = 'dashboard_message_enabled';


-- ============================================
-- 8. CHANGE MESSAGE ANYTIME
-- ============================================

-- Template:
-- UPDATE app_config SET config_value = 'YOUR MESSAGE HERE', updated_at = NOW() WHERE config_key = 'dashboard_message';

-- Example: Holiday Message
-- UPDATE app_config SET config_value = '🎄 Happy Holidays! Special rates this season.', updated_at = NOW() WHERE config_key = 'dashboard_message';


-- ============================================
-- NOTES & TIPS
-- ============================================

/*
TWILIO_NUMBERS:
- phone_number: Must be unique, E.164 format (+1234567890)
- is_active: true = can be used, false = disabled
- in_use: automatically managed by system
- status: 'available', 'in_use', 'disabled', 'maintenance'

APP_CONFIG:
- config_key: Unique identifier (use snake_case)
- config_value: Always stored as string
- For booleans: use 'true' or 'false' as strings
- For dashboard message to show:
  * dashboard_message must have text
  * dashboard_message_enabled must be 'true'

TESTING:
1. Run app: mvn spring-boot:run
2. Execute INSERT queries above
3. Open browser: http://localhost:8080
4. Popup should appear if message is enabled!

TROUBLESHOOTING:
- If popup doesn't show, check browser console (F12)
- Verify config_value is 'true' not 'True' or '1'
- Make sure message is not empty
- Clear browser cache and reload
*/

